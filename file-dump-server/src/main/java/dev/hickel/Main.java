package dev.hickel;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class Main {

    public static void main(String[] args) {
        final ActivePaths activePaths = new ActivePaths();
        final ExecutorService executor = Executors.newCachedThreadPool();
        AtomicBoolean exit = new AtomicBoolean(false);

        try { Settings.load(); } catch (IOException e) {
            System.out.println("Failed to load config, exiting...");
            throw new RuntimeException(e);
        }
        activePaths.replaceList(Settings.outputDirectories);
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));

        executor.submit(() -> {
            while (true) {
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
        InetAddress addr;
        try { addr = InetAddress.getByName(Settings.bindAddress);
        } catch (UnknownHostException e) { throw new RuntimeException("Failed to bind to address");
        }
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(addr, Settings.port));
            System.out.println("Server started. Waiting for connections...");
            while (!exit.get()) {
                Socket socket = serverSocket.accept();
                System.gc();
                executor.submit(Settings.separateThreadForWriting
                                        ? new QueuedFileReceiver(socket, activePaths)
                                        : new FileReceiver(socket, activePaths)
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}