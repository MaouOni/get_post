package com.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    private static final int PORT = 8000;
    private static boolean running = true;

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(100);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);

            while (running) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                WebServer webServer = new WebServer(socket);
                pool.execute(webServer);
            }
            pool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
