package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.LockSupport;


public class QueuedFileReceiver implements Runnable {
    private final Socket socket;
    private final ByteBuffer buffer;
    private String fileName = "";
    private long offset = 0;
    private WriteQueue writeQueue;

    public QueuedFileReceiver(Socket socket) throws SocketException {
        this.socket = socket;
        buffer = ByteBuffer.allocate(Settings.blockBufferSize);
        socket.setTcpNoDelay(false);
        socket.setReceiveBufferSize(Settings.socketBufferSize);
    }

    private void submitChunk(boolean isFinalChunk) {
        ChunkData chunk = new ChunkData(
                Arrays.copyOf(buffer.array(), buffer.position()),
                offset,
                isFinalChunk
        );
        writeQueue.submitChunk(chunk);
        offset += buffer.position();
        buffer.position(0); // Reset position, no need to clear buffer

    }

    @Override
    public void run() {
        long fileSize = 0;
        var startTime = System.currentTimeMillis();
        try (DataInputStream socketIn = new DataInputStream(socket.getInputStream());
             DataOutputStream socketOut = new DataOutputStream(socket.getOutputStream())) {

            fileName = socketIn.readUTF();
            fileSize = socketIn.readLong();

            // Check for free space, send boolean to client if space available, or file exists
            Path outputPath = null;
            Path freePath = Main.activePaths.getPath(fileSize);
            if (freePath != null && !Files.exists(Path.of(freePath + fileName))) {
                outputPath = Paths.get(freePath.toString(), fileName + ".tmp");
            } else {
                socketOut.writeBoolean(false);
                System.out.println("No free space for file: " + fileName);
                return;
            }
            writeQueue = new WriteQueue(outputPath.toFile());
            Thread writeThread = new Thread(writeQueue);
            writeThread.start();


            System.out.println("Receiving file: " + fileName);
            socketOut.writeBoolean(true);
            Main.activeTransfers.put(fileName, freePath);

            boolean receiving = true;
            while (true) {
                boolean fin = socketIn.readBoolean();
                if (fin) {
                    System.out.println("isfin");
                    submitChunk(true);
                    break;
                }

                int bytesRead = socketIn.readInt();
                byte[] bytesIn = socketIn.readNBytes(bytesRead);
                buffer.put(bytesIn);

                //Wait until buffer reaches desired chunkSize then write to disk
                if (buffer.position() >= buffer.capacity()) {
                    submitChunk(false);
                }
            }
            while (true) {
                if (writeQueue.getState() < 1) {
                    writeQueue.abort();
                    outputPath.toFile().renameTo(new File(outputPath.getParent().toFile(), fileName));
                    socketOut.writeBoolean(true); // Relay successful transfer
                    socketOut.flush();
                    Main.activeTransfers.remove(fileName);
                    long seconds = (System.currentTimeMillis() - startTime) / 1000;
                    String sb = "Finished receiving file: " + fileName +
                            "\tTime: " + seconds + " Sec" +
                            "\tSpeed: " + Math.round((double) fileSize / 1048576 / seconds) + " MiBs";
                    System.out.println(sb);
                    LockSupport.parkNanos(100000000);
                    return;
                }
            }
        } catch (IOException e) {
            Main.activeTransfers.remove(fileName);
            System.out.println("Error in dataStream or dropped connection. Aborting transfer of: " + fileName);
            e.printStackTrace();
            writeQueue.abort();
        }
    }
}