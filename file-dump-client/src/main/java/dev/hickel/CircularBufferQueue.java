package dev.hickel;

import java.io.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicIntegerArray;


public class CircularBufferQueue implements Runnable {
    private volatile byte[][] byteQueue;
    private AtomicIntegerArray indexFlags;
    private volatile int head;
    private volatile int tail;
    private volatile int lastBufferSize = Settings.chunkSize;
    private final int chunkSize = Settings.chunkSize;
    private final int capacity = Settings.readQueueSize;
    private volatile boolean finished = false;
    private File file;
    private volatile int state = 1;

    public CircularBufferQueue(File file) {
        try {
            this.file = file;
            byteQueue = new byte[Settings.readQueueSize][Settings.chunkSize];
            indexFlags = new AtomicIntegerArray(Settings.readQueueSize);
            head = 0;
            tail = 0;
            System.out.println("constructed");
            System.out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] swap(byte[] buffer, int size) {
        int currTail = tail;
        while (indexFlags.get((currTail + 1) % capacity) > 0) {
            Thread.onSpinWait();
        }
        byteQueue[currTail] = buffer;
        indexFlags.set(currTail, 2);
        lastBufferSize = size;
        int nextTail = (currTail + 1) % capacity; // inc safe since only this thread mutates
        indexFlags.set(nextTail, 1);
        tail = nextTail; //
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

    public boolean onePollLeft() {
        return finished && ((head + 1) % capacity == tail);
    }

    public int lastBytes() {
        return lastBufferSize;
    }

    public void finishedRead() {
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
        System.out.flush();
        byte[] buffer = getFirst();
        try (FileInputStream inputFile = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = inputFile.read(buffer, 0, chunkSize)) != -1 && state > 0) {
                buffer = swap(buffer, bytesRead);
            }
            finished = true;
        } catch (IOException e) {
            System.out.println("Error reading file");
            state = -1;
        } finally {
            System.out.println("closed");
        }
    }
}

