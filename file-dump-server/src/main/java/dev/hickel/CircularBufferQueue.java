package dev.hickel;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerArray;


public class CircularBufferQueue implements Runnable {
    private volatile byte[][] byteQueue;
    private final AtomicIntegerArray indexFlags;
    private volatile int head;
    private volatile int tail;
    private volatile int endBufferSize = Settings.blockBufferSize;
    private final int blockBufferSize = Settings.blockBufferSize;
    private final int capacity = Settings.queueSize;
    private volatile boolean finished = false;
    private final File file;
    private volatile int state = 1;

    public CircularBufferQueue(File file) {
        this.file = file;
        byteQueue = new byte[Settings.queueSize][Settings.blockBufferSize];
        indexFlags = new AtomicIntegerArray(Settings.queueSize);
        head = 0;
        tail = 0;
    }

    public byte[] swap(byte[] buffer, boolean isLast, int size) {
        int currTail = tail;
        byteQueue[currTail] = buffer;
        indexFlags.set(currTail, 2);
        while (indexFlags.get((currTail + 1) % capacity) > 0) {
            Thread.onSpinWait();
        }
        if (isLast) {
            finished = true;
            endBufferSize = size;
        }
        int nextTail = (currTail + 1) % capacity; // inc safe since only this thread mutates
        indexFlags.set(nextTail, 1);
        tail = nextTail;
        return byteQueue[nextTail];
    }

    public byte[] getFirst() {
        indexFlags.set(0, 1);
        return byteQueue[0];
    }

    public int getState() {
        return state;
    }

    public byte[] poll() {
        int currHead = head % capacity;
        while (isEmpty() || indexFlags.get(currHead) < 2) {
            Thread.onSpinWait();
        }
        return byteQueue[currHead];
    }

    public void wrote() {
        int oldHead = head;
        head = (oldHead + 1) % capacity; //inc  safe since only this thread mutates
        indexFlags.set(oldHead, 0);
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public void close() {
        state = 0;
    }

    @Override
    public void run() {
        try (FileOutputStream outputFile = new FileOutputStream(file);
             BufferedOutputStream bufferStream = Settings.writeBufferSize < 0
                     ? new BufferedOutputStream(outputFile)
                     : new BufferedOutputStream(outputFile, Settings.writeBufferSize)) {

            while (state > 0) {
                byte[] nextWrite = poll();
                if (finished && ((head + 1) % capacity == tail)) {
                    bufferStream.write(nextWrite, 0, endBufferSize);
                    bufferStream.flush();
                    state = 0;
                } else {
                    bufferStream.write(nextWrite, 0, blockBufferSize);
                }
                wrote();
            }
        } catch (IOException e) {
            state = -1;
            System.out.println("write error");
        }
    }
}

