package com.example.reactive.server;

import java.nio.ByteBuffer;

import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Connection extends Disposable {

    Flux<ByteBuffer> receive();

    Mono<Void> send(Publisher<ByteBuffer> dataStream);
}
