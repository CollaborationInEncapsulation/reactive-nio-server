package com.example.reactive.server;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.function.Function;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import static pl.touk.throwing.ThrowingConsumer.unchecked;
import static pl.touk.throwing.ThrowingRunnable.unchecked;

public class DefaultReactiveServer implements ReactiveServer {

    private Function<Connection, Mono<Void>> connectionsHandler;

    private final InetSocketAddress address;

    DefaultReactiveServer(String host, int port) {
        this.address = InetSocketAddress.createUnresolved(host, port);
    }

    @Override
    public ReactiveServer handle(Function<Connection, Mono<Void>> connectionsHandler) {
        this.connectionsHandler = connectionsHandler;
        return this;
    }

    @Override
    public Mono<Void> start() {
        return Flux
            .create(unchecked(sink -> {
                var connections = new HashMap<SocketChannel, Tuple2<FluxSink<SelectionKey>, FluxSink<SelectionKey>>>();
                var server = ServerSocketChannel.open()
                                                .bind(new InetSocketAddress(address.getHostName(), address.getPort()))
                                                .configureBlocking(false);
                var selector = Selector.open();

                server.register(selector, SelectionKey.OP_ACCEPT);

                sink.onDispose(() -> unchecked(server::close));

                while (true) {
                    selector.select(unchecked(key -> {
                        if (key.isValid()) {
                            if (key.isAcceptable()) {
                                var ssc = (ServerSocketChannel) key.channel();
                                var sc = ssc.accept(); // never null, nonblocking

                                sc.configureBlocking(false);

                                var readsProcessor = UnicastProcessor.create(Queues.<SelectionKey>one().get());
                                var writesProcessor = UnicastProcessor.create(Queues.<SelectionKey>one().get());

                                connections.put(sc, Tuples.of(writesProcessor.sink(), readsProcessor.sink()));

                                sink.next(connectionsHandler.apply(new DefaultConnection(
                                    sc,
                                    key,
                                    readsProcessor,
                                    writesProcessor
                                )).subscribe());
                            }
                            else if (key.isReadable()) {
                                connections.get(key.channel())
                                           .getT1()
                                           .next(key);
                            }
                            else if (key.isWritable()) {
                                connections.get(key.channel())
                                           .getT2()
                                           .next(key);
                            }
                        }
                    }));
                }
            }))
            .subscribeOn(Schedulers.newSingle(DefaultReactiveServer.class.getSimpleName()))
            .collectList()
            .doOnDiscard(Disposable.class, Disposable::dispose)
            .then();
    }
}
