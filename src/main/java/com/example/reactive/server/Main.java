package com.example.reactive.server;

import java.io.IOException;
import java.nio.ByteBuffer;

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
