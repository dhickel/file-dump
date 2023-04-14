package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class PathQueue {
    private volatile List<Path> pathList = new CopyOnWriteArrayList<>();

    public void add(Path path) { pathList.add(path); }

    public void replaceList(List<Path> pathList) { this.pathList = new CopyOnWriteArrayList<>(pathList); }

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
            Main.activeTransfers.put(fileName, pathMostFree);
        }
        return pathMostFree;
    }

    private Path getPathMostFree(long fileSize) {
        Path pathMostFree = null;
        long mostFreeSpace = 0;
        for (var path : pathList) {
            if (Settings.oneTransferPerDirectory && Main.activeTransfers.containsValue(path)) {
                continue;
            }
            long size = checkFreeSpace(path, fileSize);
            if (size > mostFreeSpace) {
                pathMostFree = path;
                mostFreeSpace = size;
            }
        }
        return pathMostFree;
    }

    public long checkFreeSpace(Path path, long fileSize) {
        long availableSpace = 0;
        try {
            if (!Files.exists(path)) { return -1; }
            FileStore fileStore = Files.getFileStore(path);
            availableSpace = fileStore.getUsableSpace();
        } catch (IOException e) {
            System.out.println("Removing path: " + path + "\tReason: Failed to read path");
            pathList.remove(path);
        }
        if (availableSpace > fileSize) {
            return availableSpace;
        } else {
            System.out.println("Out of space on path: " + path);
            return -1;
        }
    }

    public Path deleteForFreeSpace(long fileSize) throws IOException {
        for (var path : Settings.deletionDirectories) {
            if (Settings.oneTransferPerDirectory && Main.activeTransfers.containsValue(path)) {
                continue;
            }
            if (!Files.exists(path)) { continue; }
            FileStore fileStore = Files.getFileStore(path);
            for (var file : path.toFile().listFiles()) {
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
        }
        return null;
    }

    private String getExt(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.') + 1);
    }
}

