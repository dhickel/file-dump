package dev.hickel;

import java.io.*;
import java.util.concurrent.LinkedBlockingQueue;


public class WriteQueue implements Runnable {
    private final LinkedBlockingQueue<byte[]> writeQueue = new LinkedBlockingQueue<>(Settings.queueSize);
    private final File file;
    private volatile int state = 2;

    public WriteQueue(File outputFile) throws FileNotFoundException {
        this.file = outputFile;
    }

    public void submitChunk(byte[] chunk) {
        try {
            writeQueue.put(chunk);
        } catch (InterruptedException e) {
            state = -1;
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try (FileOutputStream outputFile = new FileOutputStream(file);
             BufferedOutputStream bufferStream = Settings.writeBufferSize < 0
                     ? new BufferedOutputStream(outputFile)
                     : new BufferedOutputStream(outputFile, Settings.writeBufferSize)) {
            while (state > 1) {
                bufferStream.write(writeQueue.take());
            }

            while (state > 0 && !writeQueue.isEmpty()) {
                bufferStream.write(writeQueue.take());
                bufferStream.flush();
            }
            state = 0;

        } catch (InterruptedException | IOException e) {
            System.out.println("Error writing chunk to file.");
            e.printStackTrace();
            state = -1;
        }
    }

    public void setFinished() {
        state = 1;
    }

    public int getState() {
        return state;
    }

    public int getSize() {
        return writeQueue.size();
    }

    public void close() {
        state = -2;
    }

}
