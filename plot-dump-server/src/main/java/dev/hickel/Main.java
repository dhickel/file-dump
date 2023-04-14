package dev.hickel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class Main {
    public static ConcurrentHashMap<String, Path> activeTransfers = new ConcurrentHashMap<>();
    public static final PathQueue activePaths = new PathQueue();

    public static void main(String[] args) {
        final ExecutorService executor = Executors.newCachedThreadPool();
        AtomicBoolean exit = new AtomicBoolean(false);

        try { Settings.load(); } catch (IOException e) {
            System.out.println("Failed to load config, exiting...");
            throw new RuntimeException(e);
        }
        activePaths.replaceList(Settings.outputDirectories);

        // Give option to allow transfers to finish before closing
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdownNow();
            exit.set(true);
            try {
                Scanner sc = new Scanner(System.in);
                String input = "";
                while (true) {
                    System.out.println("Abort Existing Transfers? (y/n)");
                    input = sc.nextLine();
                    if (input.equals("y")) {
                        System.out.println("Aborting existing transfers.");
                        executor.awaitTermination(0, TimeUnit.MILLISECONDS);
                        break;
                    } else if (input.equals("n")) {
                        executor.awaitTermination(60, TimeUnit.MINUTES);
                        System.out.println("Waiting up to 60 min for transfers to complete.");
                        break;
                    }
                }
            } catch (InterruptedException ignored) { }
        }));

        executor.submit(() -> {
            while (!exit.get()) {
                try {
                    Thread.sleep(6_000);
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
                var s = executor.submit(Settings.separateThreadForWrite
                                                ? new QueuedFileReceiver(socket)
                                                : new FileReceiver(socket)
                );
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}