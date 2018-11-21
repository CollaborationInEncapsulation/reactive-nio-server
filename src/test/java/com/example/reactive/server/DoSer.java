package com.example.reactive.server;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static pl.touk.throwing.ThrowingRunnable.unchecked;

public class DoSer {

    public static void main(String[] args) {
        Socket[] sockets = new Socket[3000];
        for (int i = 0; i < sockets.length; i++) {
            try {
                sockets[i] = new Socket("localhost", 8080);
                System.out.println(i);
            } catch (IOException e) {
                System.err.println("Error connecting " + e);
            }
        }

        ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime()
                                                                      .availableProcessors());
        while (true) {
            for (Socket s : sockets) {
                service.submit(unchecked(() -> {
                    s.getOutputStream()
                     .write("Hello DoS Attack".getBytes());
                    while (s.getInputStream().available() > 0) {
                        s.getInputStream().read();
                    }
                    System.out.println("Wrote");
                }));
            }
        }
    }
}
