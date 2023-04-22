package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;


public class Main {
    public static final ConcurrentHashMap<String, String> activeTransfers = new ConcurrentHashMap<>(10);

    public static void main(String[] args) throws IOException {
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
                Settings.maxTransfers.stream().mapToInt(Integer::intValue).sum() + 1
        );

        try { Settings.load(); } catch (IOException e) {
            System.out.println("Failed to load config, exiting...");
            throw new RuntimeException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));

        final Predicate<File> eligibleFile = file -> file.isFile() && !activeTransfers.containsKey(file.getName())
                && Settings.monitoredFileTypes.contains(getExt(file));

        final BiPredicate<String, Integer> limitMet = (address, limit) ->
                activeTransfers.values().stream().filter(e -> e.equals(address)).count() >= limit;

        // TODO make this not keep looping if files existing on server, getting resent then rejected and never progressing,
        //  this is also broken for transferring without deleting as it will also keep sending the same file
        //  issue is it can't see if a transfer is rejected, so will likely need to keep track of them in an array,
        //  works fine atm as long as deleting files, and not trying to keep sending the same file, which it will do if not deleting
        executor.scheduleAtFixedRate(() -> {
            try {
                Settings.load(); // Can just stick this here instead of giving it its own thread
                for (int i = 0; i < Settings.serverAddresses.size(); i++) {
                    final String addr = Settings.serverAddresses.get(i);
                    final int limit = Settings.maxTransfers.get(i);
                    final int port = Settings.serverPorts.get(i);
                    final int speedCap = Settings.transferSpeedCaps.get(i);
                    if (limitMet.test(addr + port, Settings.maxTransfers.get(i))) { continue; }
                    Settings.monitoredDirectories.stream()
                            .map(File::new)
                            .map(dir -> Optional.ofNullable(dir.listFiles()))
                            .flatMap(optFiles -> optFiles.stream().flatMap(Stream::of))
                            .forEach(file -> {
                                if (eligibleFile.test(file) && !limitMet.test(addr + port, limit)) {
                                    try {
                                        activeTransfers.put(file.getName(), addr + port);
                                        executor.submit(Settings.separateThreadForReading
                                                                ? new QueuedFileSender(file, addr, port, speedCap)
                                                                : new FileSender(file, addr, port, speedCap));
                                    } catch (IOException e) {
                                        System.out.println("Failed to connect to server: " + file);
                                        activeTransfers.remove(file.getName());
                                    }
                                }
                            });
                }

            } catch (UnsupportedOperationException e) {
                System.out.println("Error in permissions accessing a path,  make sure userspace has " +
                                           "appropriate permissions, as this prevents the monitoring of file changes");
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("Error hot loading config");
            } catch (Exception e) {
                System.out.println("Unknown error, if index out of bounds, make sure all your address,ports, " +
                                           "transfer amount and limits are the same size");
            }
        }, 0, Settings.fileCheckInterval, TimeUnit.SECONDS);
    }

    private static String getExt(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.') + 1);
    }


}
