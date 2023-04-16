package dev.hickel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;


public class QueuedFileReceiver implements Runnable {
    private final Socket socket;
    private final ActivePaths activePaths;
    private final ByteBuffer buffer;
    private String fileName = "";
    private WriteQueue writeQueue;

    public QueuedFileReceiver(Socket socket, ActivePaths activePaths) throws SocketException {
        this.socket = socket;
        this.activePaths = activePaths;
        buffer = ByteBuffer.allocate(Settings.blockBufferSize);
        socket.setTcpNoDelay(false);
        if (Settings.socketBufferSize > 0) { socket.setReceiveBufferSize(Settings.socketBufferSize); }
    }

    private void submitChunk(boolean isFinalChunk) {
        writeQueue.submitChunk(Arrays.copyOf(buffer.array(), buffer.position()));
        if (isFinalChunk) { writeQueue.setFinished(); }
        buffer.position(0);
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
            writeQueue = new WriteQueue(outputFile);
            new Thread(() -> writeQueue.run()).start();
            System.out.println("Receiving file: " + fileName +" to: " + outputFile.getParentFile());
            socketOut.writeBoolean(true);
            socketOut.flush();

            while (true) {
                // Submit any remaining buffer if server is finished sending
                boolean finished = socketIn.readBoolean();
                if (finished) {
                    submitChunk(true);
                    break;
                }

                // Read buffer
                int bytesRead = socketIn.readInt();
                byte[] bytesIn = socketIn.readNBytes(bytesRead);
                buffer.put(bytesIn);

                //Wait until buffer reaches desired chunkSize then write to disk
                if (buffer.position() >= buffer.capacity()) {
                    submitChunk(false);
                }

                //Check for writeQueue error
                if (writeQueue.getState() < 0) {
                    writeQueue.close();
                    activePaths.removePath(fileName);
                    System.out.println("Error writing file aborting....");
                    return;
                }
            }

            // Wait for queue to complete it's writes, then close socket and cleanup
            while (true) {
                if (writeQueue.getState() < 1) {
                    writeQueue.close();
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
        } catch (IOException e) {
            activePaths.removePath(fileName);
            System.out.println("Error in data stream or dropped connection. Aborting transfer of: " + fileName);
            writeQueue.close();
            e.printStackTrace();
        } catch (Exception e) {
            activePaths.removePath(fileName);
            writeQueue.close();
            e.printStackTrace();
        }
    }
}