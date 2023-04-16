package dev.hickel;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;


public class CircularBufferQueue implements Runnable {
    private final byte[][] byteQueue;
    private final int[] indexFlags;
    private volatile int head;
    private volatile int tail;
    private volatile int endBufferSize = Settings.blockBufferSize;
    private volatile int blockBufferSize = Settings.blockBufferSize;
    private final int capacity = Settings.queueSize;
    private boolean finished = false;
    private final File file;
    private volatile int state = 1;

    public CircularBufferQueue(File file) {
        this.file = file;
        byteQueue = new byte[Settings.queueSize][Settings.blockBufferSize];
        indexFlags = new int[Settings.queueSize];
        Arrays.fill(indexFlags, 0);
        head = 0;
        tail = 0;
    }

    public byte[] swap(boolean isLast, int size) {
        int currTail = tail;
        while (indexFlags[(currTail + 1) % capacity] > 0) {
            LockSupport.parkNanos(1000);
        }
        indexFlags[currTail] = 2;
        if (isLast) {
            this.finished = true;
            endBufferSize = size;
        }
        int nextTail = (currTail + 1) % capacity; // safe since only this thread mutates
        indexFlags[nextTail] = 1;
        tail = nextTail;
        return byteQueue[nextTail];
    }

    public byte[] getFirst() {
        indexFlags[0] = 1;
        return byteQueue[0];
    }

    public int getState() {
        return state;
    }

    public byte[] poll() {
        int currHead = head % capacity;
        while (isEmpty() || indexFlags[currHead] < 2) {
            LockSupport.parkNanos(1000);
        }
        return byteQueue[currHead];
    }

    public void wrote() {
        int oldHead = head;
        head = (oldHead + 1) % capacity; // safe since only this thread mutates
        indexFlags[oldHead] = 0;
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
                if (finished && (head == tail - 1)) {
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

