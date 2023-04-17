package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;


public class FileSender implements Runnable {
    private final String fileName;
    private final long fileSize;
    private final File file;
    private final int chunkSize;
    private final int blockSize;

    public FileSender(File file) throws IOException {
        fileSize = file.length();
        fileName = file.getName();
        this.file = file;
        chunkSize = Settings.chunkSize;
        blockSize = Settings.blockSize;
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(Settings.serverAddress, Settings.serverPort);
             DataOutputStream socketOut = new DataOutputStream(socket.getOutputStream());
             DataInputStream socketIn = new DataInputStream(socket.getInputStream())) {

            socket.setTcpNoDelay(false);
            if (Settings.socketBufferSize > 0) { socket.setSendBufferSize(Settings.socketBufferSize); }

            // Send file info
            socketOut.writeUTF(fileName);
            socketOut.writeLong(fileSize);
            socketOut.flush();

            boolean accepted = socketIn.readBoolean();
            if (!accepted) {
                Main.activeTransfers.remove(fileName);
                System.out.println("No space for, file already exists, or all paths in use: " + fileName
                                           + " will retry in: " + Settings.fileCheckInterval + " Seconds");
                return;
            }

            CircularBufferQueue bufferQueue = new CircularBufferQueue(file);
            new Thread(bufferQueue).start();
            System.out.println("Started transfer of file: " + fileName);

            long totalSent = 0;
            while (true) {
                byte[] buffer;
                while (!bufferQueue.onePollLeft()) {
                    System.out.println("new buffer");
                    buffer = bufferQueue.poll();
                    for (int i = 0; i < chunkSize; i += blockSize) {
                        socketOut.writeBoolean(false);
                        socketOut.writeInt(blockSize);
                        socketOut.write(buffer, i, blockSize);
                        socketOut.flush();
                        totalSent += blockSize;
                    }
                    bufferQueue.finishedRead();
                }
                System.out.println("to fin");

                buffer = bufferQueue.poll();
                int bytesLeft = bufferQueue.lastBytes();
                int offset = 0;
                System.out.println(bytesLeft == blockSize);

                while (bytesLeft > blockSize) {
                    socketOut.writeBoolean(false);
                    socketOut.writeInt(blockSize);
                    socketOut.write(buffer, offset, blockSize);
                    socketOut.flush();
                    bytesLeft -= blockSize;
                    offset += blockSize;
                    totalSent += blockSize;
                }
                System.out.println(bytesLeft);

                bufferQueue.finishedRead();

                socketOut.writeBoolean(true);
                socketOut.writeInt(bytesLeft);
                socketOut.write(buffer, offset, bytesLeft);
                totalSent += bytesLeft;
                socketOut.flush();
                bufferQueue.printBufferState();
                System.out.println("total sent:" + totalSent);
                System.out.println("file size:" + fileSize);

                boolean success = socketIn.readBoolean(); // wait for servers last write, to avoid an exception on quick disconnect
                if (success) {
                    System.out.println("Finished transfer for file: " + fileName);
                } else {
                    System.out.println("Error during finalization of file transfer");
                    Main.activeTransfers.remove(fileName);
                    throw new IllegalStateException("Server responded to end of transfer as failed");
                }
                if (Settings.deleteAfterTransfer) {
                    Files.delete(file.toPath());
                    System.out.println("Deleted file: " + file);
                }
                Main.activeTransfers.remove(fileName);
            }
        } catch (IOException e) {
            Main.activeTransfers.remove(fileName);
            System.out.println("Error in file transfer, most likely connection was lost.");
            e.printStackTrace();
        } catch (Exception e) {
            Main.activeTransfers.remove(fileName);
            e.printStackTrace();
        }
    }
}
