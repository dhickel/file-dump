package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;


public class Main {
    public static final ConcurrentHashMap.KeySetView<String, Boolean> activeTransfers
            = ConcurrentHashMap.newKeySet(10);

    public static void main(String[] args) throws IOException {
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Settings.maxTransfers);
        final Predicate<File> eligibleDirectory = file -> file.isFile()
                && activeTransfers.size() < Settings.maxTransfers + 1;
        AtomicBoolean exit = new AtomicBoolean(false);

        try { Settings.load(); } catch (IOException e) {
            System.out.println("Failed to load config, exiting...");
            throw new RuntimeException(e);
        }



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

        executor.scheduleAtFixedRate(() -> {
            try {
                if (!exit.get()) {
                    Settings.load(); // Can just stick this here instead of giving it its own thread
                    Settings.monitoredDirectories.forEach(path -> Arrays.stream(path.toFile().listFiles())
                            .takeWhile(eligibleDirectory).filter(
                                    file -> Settings.monitoredFileTypes.contains(getExt(file))
                                            && !activeTransfers.contains(file.getName()))
                            .forEach(file -> {
                                try {
                                    executor.submit(new FileSender(file));
                                    activeTransfers.add(file.getName());
                                } catch (IOException e) { System.out.println("Failed to connect to server: " + file); }
                            }));
                }
            } catch (UnsupportedOperationException e) {
                System.out.println("Failed to open a directory");
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("Error hot loading config change");
            }
        }, 0, Settings.fileCheckInterval, TimeUnit.SECONDS);
    }

    private static String getExt(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.') + 1);
    }

}
