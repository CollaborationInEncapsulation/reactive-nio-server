package com.example.reactive.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.Exceptions;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static pl.touk.throwing.ThrowingConsumer.unchecked;
import static pl.touk.throwing.ThrowingFunction.unchecked;

public class DefaultConnection implements Connection {

    final SocketChannel      socketChannel;
    final Flux<SelectionKey> readNotifier;
    final Flux<SelectionKey> writeNotifier;
    final Scheduler          scheduler;

    volatile SelectionKey currentSelectionKey;

    DefaultConnection(
        SocketChannel socketChannel,
        SelectionKey initialSelectionKey,
        Flux<SelectionKey> readNotifier,
        Flux<SelectionKey> writeNotifier
    ) {
        this.socketChannel = socketChannel;
        this.readNotifier = readNotifier;
        this.writeNotifier = writeNotifier;
        this.scheduler = Schedulers.single(Schedulers.parallel());
        this.currentSelectionKey = initialSelectionKey;
    }

    @Override
    public void dispose() {
        var key = this.currentSelectionKey;
        if (key != null) {
            currentSelectionKey = null;
            key.cancel();
        }
    }

    @Override
    public boolean isDisposed() {
        return false;
    }

    @Override
    public Flux<ByteBuffer> receive() {
        return readNotifier
            .onBackpressureLatest()
            .doOnSubscribe(unchecked(__ -> {
                var selector = currentSelectionKey.selector();

                socketChannel.register(selector, SelectionKey.OP_READ);
                selector.wakeup();
            }))
            .publishOn(scheduler)
            .doOnCancel(this::dispose)
            .concatMap(unchecked(sk -> {
                int read;
                var buffer = ByteBuffer.allocate(1024);

                read = socketChannel.read(buffer);

                if (read > 0) {
                    currentSelectionKey = sk;

                    if (read < 1024) {
                        return Mono.just(ByteBuffer.wrap(Arrays.copyOf(buffer.array(), read)));
                    }
                    else {
                        return Mono.just(buffer.flip());
                    }
                }
                else {
                    return Mono.empty();
                }
            }), 1);
    }

    @Override
    public Mono<Void> send(Publisher<ByteBuffer> dataStream) {
        return Mono.create(s ->
            Flux.from(dataStream)
                .subscribe(new BaseSubscriber<>() {
                    ByteBuffer current;

                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        s.onCancel(subscription::cancel);
                        subscription.request(1);
                        writeNotifier
                            .doOnNext(sk -> {
                                currentSelectionKey = sk;
                                currentSelectionKey.interestOps(SelectionKey.OP_READ);
                            })
                            .publishOn(scheduler)
                            .subscribe(__ -> write());
                    }

                    @Override
                    protected void hookOnNext(ByteBuffer value) {
                        current = value;
                        write();
                    }

                    @Override
                    protected void hookOnError(Throwable throwable) {
                        s.error(throwable);
                    }

                    @Override
                    protected void hookOnComplete() {
                        s.success();
                    }

                    private void write() {
                        int result;
                        var buffer = current;

                        if (buffer == null) {
                            return;
                        }

                        try {
                            result = socketChannel.write(buffer);
                        }
                        catch (IOException e) {
                            throw Exceptions.propagate(e);
                        }

                        if (result == -1) {
                            upstream().cancel();
                        }

                        if (buffer.hasRemaining()) {
                            var key = currentSelectionKey;

                            key.interestOps(SelectionKey.OP_WRITE);
                            key.selector().wakeup();

                            return;
                        }

                        current = null;
                        upstream().request(1);
                    }
                })
        );
    }
}
