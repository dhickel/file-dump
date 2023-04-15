package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;


public class ActivePaths {
    private List<Path> pathList = new ArrayList<>();
    private final HashMap<String, Path> activeTransfers = new HashMap<>();

    public synchronized void replaceList(List<Path> pathList) {
        this.pathList.clear();
        this.pathList.addAll(pathList);
    }

    public synchronized Path getPath(String fileName, long fileSize) {
        Path pathMostFree = getPathMostFree(fileSize);
        if (pathMostFree == null && Settings.deleteForSpace) {
            try {
                pathMostFree = deleteForFreeSpace(fileSize);
            } catch (IOException | UnsupportedOperationException e) {
                System.out.println("Error deleting file(s) for more space");
                e.printStackTrace();
            }
        }
        if (pathMostFree != null) {
            Path freeFile = pathMostFree.resolve(fileName);
            if (!Settings.overWriteExisting && freeFile.toFile().exists()) {
                return null;
            }
            activeTransfers.put(fileName, pathMostFree);
            return Path.of(freeFile + ".tmp");
        }
        return null;
    }

    public synchronized void removePath(String fileName) {
        activeTransfers.remove(fileName);
    }

    final Predicate<Path> eligible = path -> path != null && path.toFile().isDirectory()
            && (!activeTransfers.containsValue(path) || !Settings.oneTransferPerDirectory);

    private Path getPathMostFree(long fileSize) {
        final List<Path> badPaths = new ArrayList<>(1);

        Path pathMostFree = pathList.stream()
                .filter(eligible)
                .map(path -> {
                    try {
                        return new AbstractMap.SimpleImmutableEntry<>(path, Files.getFileStore(path).getUsableSpace());
                    } catch (IOException e) {
                        System.out.println("Removing path: " + path + "\tReason: Failed to read path");
                        badPaths.add(path);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() > fileSize)
                .map(Map.Entry::getKey)
                .orElse(null);

        if (!badPaths.isEmpty()) {
            pathList.removeAll(badPaths);
        }
        return pathMostFree;
    }

    // Easier to read and trust than a lambda doing the same
    public Path deleteForFreeSpace(long fileSize) throws IOException {
        for (var path : Settings.deletionDirectories) {
            try {
                if (!eligible.test(path)) { continue; }
                FileStore fileStore = Files.getFileStore(path);

                for (var file : path.toFile().listFiles()) {
                    if (!file.isFile()) {continue;}
                    if (Settings.deletedFileTypes.contains(getExt(file)) && file.length() >= Settings.deletionThreshHold) {
                        try {
                            long size = file.length();
                            Files.delete(file.toPath());
                            System.out.println("Deleted file: " + file.getName()
                                                       + "\tSize: " + Math.round((double) size / 1048576) + " MiB");
                        } catch (IOException e) {
                            System.out.println("Error deleting file: " + file.getName());
                        }
                    }
                    if (fileStore.getUsableSpace() > fileSize) { return path; }
                }
            } catch (UnsupportedOperationException e) {
                System.out.println("Error in permissions accessing path:" + path);
            }
        }
        return null;
    }

    private String getExt(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.') + 1);
    }
}

