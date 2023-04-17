package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.concurrent.locks.LockSupport;


public class CircularFileReceiver implements Runnable {
    private final Socket socket;
    private final ActivePaths activePaths;
    private byte[] buffer;
    private int bufferSize;
    private CircularBufferQueue bufferQueue;
    private final int blockBufferSize;
    private String fileName = "";

    public CircularFileReceiver(Socket socket, ActivePaths activePaths) throws SocketException {
        this.socket = socket;
        this.activePaths = activePaths;
        buffer = new byte[Settings.blockBufferSize];
        socket.setTcpNoDelay(false);
        if (Settings.socketBufferSize > 0) { socket.setReceiveBufferSize(Settings.socketBufferSize); }
        blockBufferSize = Settings.blockBufferSize;
    }

    @Override
    public void run() {
        long fileSize = 0;
        var startTime = System.currentTimeMillis();
        try (DataInputStream socketIn = new DataInputStream(socket.getInputStream());
             DataOutputStream socketOut = new DataOutputStream(socket.getOutputStream())) {

            fileName = socketIn.readUTF();
            fileSize = socketIn.readLong();

            // Check for free space, send boolean to client if space not available, or file exists
            Path freePath = activePaths.getPath(fileName, fileSize);
            if (freePath == null) {
                socketOut.writeBoolean(false);
                socketOut.flush();
                System.out.println("No space for, file already exists, or all paths in use: " + fileName);
                return;
            }

            // Start writeQueue thread and inform client to begin
            File outputFile = freePath.toFile();
            bufferQueue = new CircularBufferQueue(outputFile);
            new Thread(bufferQueue).start();
            System.out.println("Receiving file: " + fileName + " to: " + outputFile.getParentFile());
            socketOut.writeBoolean(true);
            socketOut.flush();

            buffer = bufferQueue.getFirst();
            int currOffset = 0;
            while (true) {
                // Submit any remaining buffer if server is finished sending
                boolean finished = socketIn.readBoolean();
                if (finished) {
                    bufferQueue.swap(buffer, true, currOffset);
                    break;
                }

                // Read buffer
                bufferSize = socketIn.readInt();
                System.arraycopy(socketIn.readNBytes(bufferSize), 0, buffer, currOffset, bufferSize);
                currOffset += bufferSize;

                // write if full
                if (currOffset == blockBufferSize) {
                    buffer = bufferQueue.swap(buffer, false, blockBufferSize);
                    currOffset = 0;
                }

                // Check for buffer error
                if (bufferQueue.getState() < 0) {
                    bufferQueue.close();
                    activePaths.removePath(fileName);
                    System.out.println("Error writing file aborting....");
                    return;
                }
            }

            // Wait for queue to complete it's writes, then close socket and cleanup
            while (true) {
                if (bufferQueue.getState() < 1) {
                    bufferQueue.close();
                    activePaths.removePath(fileName);
                    File finalFile = new File(outputFile.getParent(), fileName);
                    outputFile.renameTo(finalFile);

                    if (!finalFile.exists() || finalFile.length() != fileSize) {
                        socketOut.writeBoolean(false); // Relay there was an issue
                        throw new IllegalStateException("Output file does not exist, or is corrupted");
                    }
                    socketOut.writeBoolean(true); // Relay successful transfer
                    socketOut.flush();

                    long seconds = (System.currentTimeMillis() - startTime) / 1000;
                    String metrics = "Finished receiving file: " + fileName + " to: " + outputFile.getParentFile() +
                            "\tTime: " + seconds + " Sec" +
                            "\tSpeed: " + Math.round((double) fileSize / 1048576 / seconds) + " MiBs";
                    System.out.println(metrics);
                    socket.close();
                    return;
                }
                LockSupport.parkNanos(1_000_000 * 50);
            }
        } catch (Exception e) {
            activePaths.removePath(fileName);
            bufferQueue.close();
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException ignored) { }
        }
    }
}

