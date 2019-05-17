package com.example.reactive.server;

import java.io.IOException;
import java.net.Socket;

public class BackpressureTest {

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 8080);

        int i = 0;
        while (true) {
            socket.getOutputStream()
                  .write("Hello DoS Attack".getBytes());

            System.out.println("Wrote Message #[" + i++ + "]");
        }
    }
}
