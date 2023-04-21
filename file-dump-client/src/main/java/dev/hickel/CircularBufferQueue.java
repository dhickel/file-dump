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
        this.file = file;
        byteQueue = new byte[Settings.readQueueSize][Settings.chunkSize];
        indexFlags = new AtomicIntegerArray(Settings.readQueueSize);
        head = 0;
        tail = 0;
    }

    // A buffer is "checked out" with the flag set to 1, once it is return full the flag is set to 2 meaning it is ready
    // to read. After a read has been done flag is return to 0 meaning it is free to be written to again.
    // buffers stay the same size, up until the last write and "null" 0 termination buffer. This allows for the last read
    // where the buffer is more than likely not going to equal the block size
    public byte[] swap(byte[] buffer, int size) {
        int currTail = tail;

        if (size < chunkSize) {
            byte[] smallBuff = new byte[size];
            System.arraycopy(buffer, 0, smallBuff, 0, size);
            byteQueue[currTail] = smallBuff;
        } else {
            byteQueue[currTail] = buffer;
        }
        lastBufferSize = size;
        indexFlags.set(currTail, 2);
        while (indexFlags.get((currTail + 1) % capacity) > 0) {
            Thread.onSpinWait();
        }
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
        int currHead = head;

        while (isEmpty() || indexFlags.get(currHead) < 2) {
            Thread.onSpinWait();
        }
        return byteQueue[currHead];
    }

    // The sets the buffers index flag back to zero to be reused, this needs to be 2 parts so data not yet written by
    // the poller doesn't get overwritten.
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

    void printInfo() {
        System.out.println("Head:" + head + "\ttail:" + tail + "\t" + indexFlags);
    }

    @Override
    public void run() {
        byte[] buffer = getFirst();
        try (FileInputStream inputFile = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = inputFile.read(buffer, 0, chunkSize)) != -1) {
                buffer = swap(buffer, bytesRead);
            }
            swap(buffer, 0);
        } catch (IOException e) {
            System.out.println("Error reading file");
            state = -1;
        }
    }
}

