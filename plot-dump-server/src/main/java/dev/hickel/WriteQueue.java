package dev.hickel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ArrayBlockingQueue;


public class WriteQueue implements Runnable{
    private final ArrayBlockingQueue<ChunkData> writeQueue= new ArrayBlockingQueue<>(10);
    private final RandomAccessFile outputFile;
    private final File file;
    private volatile int state = 1;

    public WriteQueue(File outputFile) throws FileNotFoundException {
        this.file = outputFile;
        this.outputFile = new RandomAccessFile(outputFile, "rw");
    }

    public void submitChunk(ChunkData chunk) {
        try {
            writeQueue.put(chunk);
        } catch (InterruptedException e) {
            state = -1;
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        while (state >= 0) {
            try {
                ChunkData chunk = writeQueue.take();
                outputFile.seek(chunk.offset);
                outputFile.write(chunk.data, 0, chunk.data.length);
                if (chunk.isLast) {
                    outputFile.close();
                    System.out.println("lastChunk");
                    state = 0;
                }

            } catch (InterruptedException | IOException e) {
                System.out.println("Error writing chunk to file.");
                e.printStackTrace();
                state = -1;
            }
        }
    }

    public int getState(){
        return state;
    }

    public void abort() {
        state = -2;
    }

}
