package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.locks.LockSupport;


public class QueuedFileReceiver implements Runnable {
    private final Socket socket;
    private final ActivePaths activePaths;
    private byte[] buffer;

    private CircularBufferQueue bufferQueue;
    private final int blockBufferSize;
    private String fileName = "";

    public QueuedFileReceiver(Socket socket, ActivePaths activePaths) throws SocketException {
        this.socket = socket;
        this.activePaths = activePaths;
        buffer = new byte[Settings.blockBufferSize];
        socket.setSoTimeout(120_000);
        socket.setTrafficClass(24);
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
            Path freePath = activePaths.getNewPath(fileName, fileSize);
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
            int bytesReceived;
            while (true) {
                // Submit any remaining buffer if server is finished sending

                bytesReceived = socketIn.readInt();
                if (bytesReceived == -1) {
                    bufferQueue.swap(buffer, true, currOffset);
                    break;
                }

                // Read buffer
                System.arraycopy(socketIn.readNBytes(bytesReceived), 0, buffer, currOffset, bytesReceived);
                currOffset += bytesReceived;

                // write if full
                if (currOffset == blockBufferSize) {
                    buffer = bufferQueue.swap(buffer, false, blockBufferSize);
                    currOffset = 0;
                }

                // Check for buffer error
                if (bufferQueue.getState() < 0) {
                    bufferQueue.close();
                    System.out.println("Error writing, assuming directory has improper privileges.");
                    System.out.println("Removed Path: " + activePaths.getExistingPath(fileName));
                    activePaths.removeActiveTransfer(fileName);
                    activePaths.removePathOfTransfer(fileName);
                    return;
                }
            }

            // Wait for queue to complete it's writes, then close socket and cleanup
            while (true) {
                if (bufferQueue.getState() < 1) {
                    activePaths.removeActiveTransfer(fileName);
                    File finalFile = new File(outputFile.getParent(), fileName);
                    outputFile.renameTo(finalFile);

                    if (!finalFile.exists() || finalFile.length() != fileSize) {
                        socketOut.writeBoolean(false); // Relay there was an issue
                        socketOut.close();
                        throw new IllegalStateException("Output file does not exist, or is corrupted");
                    }
                    socketOut.writeBoolean(true); // Relay successful transfer
                    socketOut.flush();
                    bufferQueue.close();

                    long seconds = (System.currentTimeMillis() - startTime) / 1000;
                    String metrics = "Finished receiving file: " + fileName + " to: " + outputFile.getParentFile() +
                            "\tTime: " + seconds + " Sec" +
                            "\tSpeed: " + Math.round((double) fileSize / 1048576 / seconds) + " MiBs";
                    int[] stats = TransferStats.incStats(fileSize);
                    System.out.println(metrics);
                    System.out.println("Daily Stats | Count: " + stats[0] +" | Transferred: " + stats[1] + " GiB" );
                    return;
                }
                LockSupport.parkNanos(1_000_000 * 50);
            }
        } catch (Exception e) {
            System.out.println(Instant.now().getEpochSecond());
            activePaths.removeActiveTransfer(fileName);
            bufferQueue.close();
            e.printStackTrace();
            try { socket.close(); } catch (IOException ee) { System.out.println("Error closing socket"); }
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException e) { System.out.println("Error closing socket"); }
            }
        }
    }
}

