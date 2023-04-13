package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.*;


public class Main {
    private static final ExecutorService executor;
    public static final ConcurrentHashMap.KeySetView<String, Boolean> activeTransfers;

    static {
        executor = Executors.newFixedThreadPool(Settings.maxTransfers);
        activeTransfers = ConcurrentHashMap.newKeySet(10);
    }


    public static void main(String[] args) {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdownNow();
            System.out.println("Exiting...Waiting for existing transfers to complete...");
            try {
                executor.awaitTermination(60, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
            }
        }));

        try {

            while (true) {
                if (activeTransfers.size() >= Settings.maxTransfers) {
                    Thread.sleep(Settings.fileCheckInterval);
                    continue;
                }
                checkForNewFiles();
            }
        } catch (InterruptedException ignored) {
        }
    }

    private static void checkForNewFiles() {
        for (var path : Settings.monitoredDirectories) {
            if (!Files.exists(path)) { continue; }
            for (var file : path.toFile().listFiles()) {
                if (!file.isFile()
                        || !Settings.monitoredFileTypes.contains(getExt(file))
                        || activeTransfers.contains(file.getName())
                ) {
                    continue;
                }
                try {
                    executor.submit(new FileSender(file));
                } catch (IOException e) {
                    System.out.println("Failed to initiate transfer of file: " + file);
                    e.printStackTrace();
                }
            }
            if (activeTransfers.size() >= Settings.maxTransfers) { return; }
        }
    }

    // Why is there not an existing method for this....
    private static String getExt(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.') + 1);
    }
}
