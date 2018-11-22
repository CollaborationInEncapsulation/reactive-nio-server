package com.example.reactive.server;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.Loggers;

import static pl.touk.throwing.ThrowingRunnable.unchecked;

public class DefaultReactiveServerTest {

    @Test
    public void testSendsAndReceivesExpectedData() throws Exception {
        var server = new AtomicReference<Disposable>();
        try {
            new Thread(() -> server.set(
                ReactiveServer
                    .create("localhost", 8080)
                    .handle(connection ->
                        connection
                            .receive()
                            .bufferUntil(bb -> new String(bb.array()).contains("-END-"))
                            .map(listOfBuffers -> {
                                int totalCapacity = 0;
                                var endBuffer = listOfBuffers.get(listOfBuffers.size() - 1);

                                endBuffer.limit(endBuffer.limit() - ("-END-").getBytes().length);

                                for (ByteBuffer bb : listOfBuffers) {
                                    totalCapacity += bb.limit();
                                }

                                var resultBuffer = ByteBuffer.allocate(totalCapacity);

                                for (ByteBuffer bb : listOfBuffers) {
                                    resultBuffer.put(bb);
                                }

                                return resultBuffer.flip();
                            })
                            .as(connection::send))
                    .start()
                    .subscribe()
            )).start();

            Thread.sleep(1000);

            var channel = SocketChannel.open(new InetSocketAddress(8080));

            channel.write(ByteBuffer.wrap("Hello".getBytes()));
            channel.write(ByteBuffer.wrap(" ".getBytes()));
            channel.write(ByteBuffer.wrap("World\n\r".getBytes()));
            channel.write(ByteBuffer.wrap("-END-".getBytes()));

            var helloWorldByteLength = "Hello World".getBytes().length;

            var buffer = ByteBuffer.allocate(helloWorldByteLength);

            Assert.assertEquals(helloWorldByteLength, channel.read(buffer));
            Assert.assertEquals("Hello World", new String(buffer.array()));
        }
        finally {
            server.get().dispose();
        }
    }


    @Test
    public void testBackpressureSupport() throws Exception {
        var connectionReference = new AtomicReference<Connection>();
        var cdl = new CountDownLatch(1);
        var server = ReactiveServer.create("localhost", 8080)
                .handle(connection -> {
                    connectionReference.set(connection);
                    cdl.countDown();

                    return Mono.never();
                })
                .start()
                .subscribe();

        try {

            var channel = SocketChannel.open(new InetSocketAddress("localhost", 8080));

            cdl.await();

            StepVerifier.create(connectionReference.get()
                                                   .receive(), 0)
                        .expectSubscription()
                        .then(unchecked(() -> channel.write(ByteBuffer.wrap("Hello World".getBytes()))))
                        .thenRequest(1)
                        .assertNext(bb -> Assert.assertEquals("Hello World", new String(bb.array()).trim()))
                        .then(unchecked(() -> channel.write(ByteBuffer.wrap("World Hello".getBytes()))))
                        .thenRequest(1)
                        .assertNext(bb -> Assert.assertEquals("World Hello", new String(bb.array()).trim()))
                        .thenCancel()
                        .verify();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    public void testEndToEndBackpressureSupport() throws Exception {
        Logger.getGlobal().setLevel(Level.INFO);
        Loggers.useJdkLoggers();
        var cdl = new CountDownLatch(1);
        var server = ReactiveServer
            .create("localhost", 8080)
            .handle(connection -> {
                cdl.countDown();
                return connection.receive()
                                 .as(connection::send);
            })
            .start()
            .subscribe();

        try {
            Thread.sleep(1000);

            var channel = SocketChannel.open(new InetSocketAddress(8080));
            channel.configureBlocking(false);

            cdl.await();

            var wrote = 0;
            var bytes = "A".getBytes();

            do {
                wrote = channel.write(ByteBuffer.wrap(bytes));
            }
            while (wrote == bytes.length);

            var readBB = ByteBuffer.allocate(bytes.length);

            do {
                channel.read(readBB);

                Assert.assertArrayEquals(readBB.array(), "A".getBytes());
                readBB.clear();
            } while (channel.write(ByteBuffer.wrap(bytes)) == 0);

            Assert.assertEquals(bytes.length, channel.write(ByteBuffer.wrap(bytes)));

        } finally {
            server.dispose();
        }

    }
}