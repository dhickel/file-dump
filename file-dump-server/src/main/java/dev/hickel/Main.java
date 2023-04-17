package dev.hickel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class Main {

    public static void main(String[] args) {
        final ActivePaths activePaths = new ActivePaths();
        final ExecutorService executor = Executors.newCachedThreadPool();
        AtomicBoolean exit = new AtomicBoolean(false);

//        try { Settings.load(); } catch (IOException e) {
//            System.out.println("Failed to load config, exiting...");
//            throw new RuntimeException(e);
//        }
        activePaths.replaceList(Settings.outputDirectories);

        // Give option to allow transfers to finish before closing
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));

        executor.submit(() -> {
            while (!exit.get()) {
                try {
                    Thread.sleep(60_000);
                    boolean changed = Settings.load();
                    if (changed) { activePaths.replaceList(Settings.outputDirectories); }
                } catch (IOException e) {
                    System.out.println("Error hot loading config change");
                } catch (InterruptedException ignored) { }
            }
        });

        // wait for new connections
        try (ServerSocket serverSocket = new ServerSocket(Settings.port)) {
            System.out.println("Server started. Waiting for connections...");
            while (!exit.get()) {
                Socket socket = serverSocket.accept();
                if (exit.get()) { return; }
                switch(Settings.writeStyle) {
                    case 1 ->  executor.submit(new FileReceiver(socket, activePaths));
                    case 2 ->  executor.submit(new QueuedFileReceiver(socket, activePaths));
                    case 3 ->  executor.submit(new CircularFileReceiver(socket, activePaths));
                    default -> System.out.println("Write Style must be 1-3, fix config to continue.");
                }
                Thread.sleep(1000); // Sleep a little before accepting another connection
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}