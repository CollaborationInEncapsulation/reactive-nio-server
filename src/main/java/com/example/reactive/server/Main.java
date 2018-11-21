package com.example.reactive.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static pl.touk.throwing.ThrowingConsumer.unchecked;
import static pl.touk.throwing.ThrowingRunnable.unchecked;

public class Main {

    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(8080);
        ExecutorService executorService =
                Executors.newCachedThreadPool();

        while (true) {
        	Socket s = ss.accept();
            executorService.submit(unchecked(() -> {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                OutputStream out = s.getOutputStream();
                in.lines()
                  .forEach(unchecked(line -> out.write(("Echo: " + line + "\n").getBytes())));
                s.close();
            }));
        }
    }
}
