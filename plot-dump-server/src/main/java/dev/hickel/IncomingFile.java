package dev.hickel;

import java.io.RandomAccessFile;
import java.nio.file.Path;


public  class IncomingFile {
    public final RandomAccessFile file;
    public final Path path;

    public IncomingFile(RandomAccessFile file, Path path) {
        this.file = file;
        this.path = path;
    }
}
