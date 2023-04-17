package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;


public class Main {
    public static final ConcurrentHashMap.KeySetView<String, Boolean> activeTransfers
            = ConcurrentHashMap.newKeySet(10);

    public static void main(String[] args) throws IOException {
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Settings.maxTransfers);

        try { Settings.load(); } catch (IOException e) {
            System.out.println("Failed to load config, exiting...");
            throw new RuntimeException(e);
        }

        executor.submit(new FileSender(new File("/media/mindspice/Games/sdiff2/models/Stable-diffusion/sd-v1-4-full-ema.ckpt")));


        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));

        final Predicate<File> eligibleDirectory = file -> !activeTransfers.contains(file.getName())
                && activeTransfers.size() < Settings.maxTransfers;

//        executor.scheduleAtFixedRate(() -> {
//            try {
//                if (!exit.get()) {
//                    Settings.load(); // Can just stick this here instead of giving it its own thread
//                    Settings.monitoredDirectories.stream()
//                            .map(File::new)
//                            .map(dir -> Optional.ofNullable(dir.listFiles()))
//                            .flatMap(optFiles -> optFiles.stream().flatMap(Stream::of))
//                            .filter(File::isFile)
//                            .filter(file -> Settings.monitoredFileTypes.contains(getExt(file)))
//                            .filter(eligibleDirectory)
//                            .forEach(file -> {
//                                try {
//                                    activeTransfers.add(file.getName());
//                                    executor.submit(Settings.separateThreadForReading
//                                                            ? new QueuedFileSender(file)
//                                                            : new FileSender(file)
//                                    );
//                                } catch (IOException e) {
//                                    System.out.println("Failed to connect to server: " + file);
//                                    ;
//                                }
//                            });
//                }
//            } catch (UnsupportedOperationException e) {
//                System.out.println("Error in permissions accessing a path,  make sure userspace has " +
//                                           "appropriate permissions, as this prevents the monitoring of file changes");
//                e.printStackTrace();
//            } catch (IOException e) {
//                System.out.println("Error hot loading config");
//            }
//        }, 0, Settings.fileCheckInterval, TimeUnit.SECONDS);
    }

        private static String getExt (File file){
            return file.getName().substring(file.getName().lastIndexOf('.') + 1);
        }

    }
