package dev.hickel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.LinkedBlockingQueue;


public class ReadQueue implements Runnable {
    private final LinkedBlockingQueue<byte[]> writeQueue = new LinkedBlockingQueue<>(128);
    private final File file;
    private volatile int state = 2;
    private final ByteBuffer buffer = ByteBuffer.allocate(Settings.blockSize);

    public ReadQueue(File inputFile) throws FileNotFoundException {
        this.file = inputFile;
    }

    @Override
    public void run() {
        try (FileInputStream inputFile = new FileInputStream(file);
             FileChannel inputChannel = inputFile.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate(Settings.blockSize);
            while ((inputChannel.read(buffer)) != -1 && state > 0) {
                byte[] bytes = new byte[buffer.position()];
                System.arraycopy(buffer.array(), 0, bytes, 0, buffer.position());
                writeQueue.put(bytes);
                buffer.position(0); // Prepare the buffer for the next read
            }
            writeQueue.put(new byte[0]); // EOF flag
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public byte[] getNextChunk () throws InterruptedException {
        return writeQueue.take();
    }

    public void stop(){
        state = -1;
    }
}
