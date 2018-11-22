package com.example.reactive.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static pl.touk.throwing.ThrowingConsumer.unchecked;
import static pl.touk.throwing.ThrowingFunction.unchecked;

public class DefaultConnection extends BaseSubscriber<ByteBuffer> implements Connection {

    final SocketChannel      socketChannel;
    final Flux<SelectionKey> readNotifier;
    final Flux<SelectionKey> writeNotifier;
    final Scheduler          scheduler;

    volatile SelectionKey currentSelectionKey;

    ByteBuffer current;

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
    public void close() {
        var key = currentSelectionKey;

        if (key != null) {
            dispose();
            currentSelectionKey = null;
            key.cancel();
        }
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
            .doOnNext(sk -> currentSelectionKey = sk)
            .publishOn(scheduler)
            .doOnCancel(this::close)
            .concatMap(unchecked(sk -> {
                var buffer = ByteBuffer.allocate(1024);
                var read = socketChannel.read(buffer);

                if (read > 0) {
                    return Mono.just(buffer.flip());
                }

                return Mono.empty();
            }));
    }

    @Override
    public Mono<Void> send(Publisher<ByteBuffer> dataStream) {
        return Mono
            .<Void>fromRunnable(() ->
                Flux.from(dataStream)
                    .subscribe(this)
            )
            .doOnCancel(this::close);
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        subscription.request(1);
        writeNotifier
                .doOnNext(sk -> {
                    currentSelectionKey = sk;
                    currentSelectionKey.interestOps(SelectionKey.OP_READ);
                })
                .publishOn(scheduler)
                .subscribe(__ -> hookOnNext(current));
    }

    @Override
    protected void hookOnNext(ByteBuffer buffer) {
        int result;

        try {
            result = socketChannel.write(buffer);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (result == -1) {
            upstream().cancel();
        }

        if (buffer.hasRemaining()) {
            current = buffer;
            var key = currentSelectionKey;

            key.interestOps(SelectionKey.OP_WRITE);
            key.selector().wakeup();

            return;
        }

        upstream().request(1);
    }
}
