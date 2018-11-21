package com.example.reactive.server;

import java.util.function.Function;

import reactor.core.publisher.Mono;

public interface ReactiveServer {

    static ReactiveServer create(String host, int port) {
        return new DefaultReactiveServer(host, port);
    }

    ReactiveServer handle(Function<Connection, Mono<Void>> connectionsHandler);

    Mono<Void> start();
}
