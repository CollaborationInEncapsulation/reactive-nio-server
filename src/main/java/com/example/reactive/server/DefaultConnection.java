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
import static pl.touk.throwing.ThrowingBiConsumer.unchecked;

public class DefaultConnection implements Connection {

    @Override
    public void close() {

    }

    @Override
    public Flux<ByteBuffer> receive() {
        return null;
    }

    @Override
    public Mono<Void> send(Publisher<ByteBuffer> dataStream) {
       return null;
    }
}
