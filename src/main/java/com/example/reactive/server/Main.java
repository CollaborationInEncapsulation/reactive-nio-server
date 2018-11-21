package com.example.reactive.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static pl.touk.throwing.ThrowingConsumer.unchecked;
import static pl.touk.throwing.ThrowingRunnable.unchecked;

public class Main {

    public static void main(String[] args) throws IOException {
        ReactiveServer.create("localhost", 8080)
                      .handle(c ->
                          c.receive()
                           .map(b -> ByteBuffer.wrap(("Echo: " + new String(b.array()).trim() + "\n").getBytes()))
                           .as(c::send)
                      )
                      .start()
                      .block();
    }
}
