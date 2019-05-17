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
        return null;
    }
}
