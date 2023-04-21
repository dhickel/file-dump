package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Stream;


public class Main {
    public static final ConcurrentHashMap.KeySetView<String, Boolean> activeTransfers
            = ConcurrentHashMap.newKeySet(10);

    public static void main(String[] args) throws IOException {
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Settings.maxTransfers + 1);

        try { Settings.load(); } catch (IOException e) {
            System.out.println("Failed to load config, exiting...");
            throw new RuntimeException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));

        final Predicate<File> eligibleDirectory = file -> !activeTransfers.contains(file.getName())
                && activeTransfers.size() < Settings.maxTransfers;

        // TODO make this not keep looping if files existing on server, getting resent then rejected and never progressing,
        //  this is also broken for transffering without deleting as it will also keep sending the same file
        //  issue is it can't see if a transfer is rejected, so will likely need to keep track of them in an array,
        //  works fine atm as long as deleting files, and not trying to keep sending the same file, which it will do if not deleting
        executor.scheduleAtFixedRate(() -> {
            try {
                Settings.load(); // Can just stick this here instead of giving it its own thread
                Settings.monitoredDirectories.stream()
                        .map(File::new)
                        .map(dir -> Optional.ofNullable(dir.listFiles()))
                        .flatMap(optFiles -> optFiles.stream().flatMap(Stream::of))
                        .filter(File::isFile)
                        .filter(file -> Settings.monitoredFileTypes.contains(getExt(file)))
                        .filter(eligibleDirectory)
                        .forEach(file -> {
                            try {
                                System.gc();
                                activeTransfers.add(file.getName());
                                executor.submit(Settings.separateThreadForReading
                                                        ? new QueuedFileSender(file)
                                                        : new FileSender(file));
                            } catch (IOException e) {
                                System.out.println("Failed to connect to server: " + file);
                                activeTransfers.remove(file.getName());
                            }
                        });

            } catch (UnsupportedOperationException e) {
                System.out.println("Error in permissions accessing a path,  make sure userspace has " +
                                           "appropriate permissions, as this prevents the monitoring of file changes");
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("Error hot loading config");
            }
        }, 0, Settings.fileCheckInterval, TimeUnit.SECONDS);
    }

    private static String getExt(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.') + 1);
    }

}
