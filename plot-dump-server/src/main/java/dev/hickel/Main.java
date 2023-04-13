package dev.hickel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class Main {
    public static volatile ConcurrentHashMap<String, Path> activeTransfers = new ConcurrentHashMap<>();
    public static final PathQueue activePaths = new PathQueue();

    public static void main(String[] args) throws IOException {
        final ExecutorService executor = Executors.newCachedThreadPool();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdownNow();
            System.out.println("Exiting...Waiting for existing transfers to complete...");
            try {
                executor.awaitTermination(60, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
            }
        }));


        activePaths.replaceList(Settings.outputDirectories);

        try (ServerSocket serverSocket = new ServerSocket(Settings.port)) {
            System.out.println("Server started. Waiting for connections...");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Connection accepted: " + socket);

                executor.submit(new FileReceiver(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}