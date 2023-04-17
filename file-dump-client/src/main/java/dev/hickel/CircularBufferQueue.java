package dev.hickel;

import java.io.*;
import java.util.concurrent.atomic.AtomicIntegerArray;


public class CircularBufferQueue implements Runnable {
    private volatile byte[][] byteQueue;
    private final AtomicIntegerArray indexFlags;
    private volatile int head;
    private volatile int tail;
    private volatile int lastBufferSize = Settings.chunkSize;
    private final int chunkSize = Settings.chunkSize;
    private final int capacity = Settings.readQueueSize;
    private volatile boolean finished = false;
    private final File file;
    private volatile int state = 1;
    private volatile int pollCount = 0;
    private volatile int putCount =0;

    public CircularBufferQueue(File file) {
        this.file = file;
        byteQueue = new byte[Settings.readQueueSize][Settings.chunkSize];
        indexFlags = new AtomicIntegerArray(Settings.readQueueSize);
        head = 0;
        tail = 0;
    }

    public byte[] swap(byte[] buffer, int size) {
        putCount++;
        int currTail = tail;
        while (indexFlags.get((currTail + 1) % capacity) > 0) {
            Thread.onSpinWait();
        }
        byteQueue[currTail] = buffer;
        indexFlags.set(currTail, 2);
        lastBufferSize = size;
        int nextTail = (currTail + 1) % capacity; // inc safe since only this thread mutates
        indexFlags.set(nextTail, 1);
        tail = nextTail;
        System.out.println("Swap | Head:" + head + "\tTail:" + tail +"\t\tFlags:" + indexFlags);
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
        pollCount++;
        System.out.println("Poll | Head:" + head + "\tTail:" + tail +"\t\tFlags:" + indexFlags);
        int currHead = head % capacity;
        while (isEmpty() || indexFlags.get(currHead) < 2) {
            Thread.onSpinWait();
        }
        return byteQueue[currHead];
    }

    public boolean onePollLeft() {
        return finished && (head  ==  tail -1);
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

    public void printBufferState() {
        System.out.println("Swap | Head:" + head + "\tTail:" + tail +"\t\tFlags:" + indexFlags);
        System.out.println("poll: " + pollCount + "\tput:" + putCount);
    }

    @Override
    public void run() {
        byte[] buffer = getFirst();
        try (FileInputStream inputFile = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = inputFile.read(buffer, 0, chunkSize)) != -1 && state > 0) {
                if (bytesRead == chunkSize) {
                    buffer = swap(buffer,bytesRead);
                } else {
                    swap(buffer, bytesRead);
                }
            }
            finished = true;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

