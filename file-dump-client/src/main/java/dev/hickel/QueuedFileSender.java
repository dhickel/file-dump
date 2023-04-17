package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;


public class QueuedFileSender implements Runnable {
    private final String fileName;
    private final long fileSize;
    private final File file;
    private final int chunkSize;
    private final int blockSize;
    private final ReadQueue readQueue;
    private Thread readerThread;

    public QueuedFileSender(File file) throws IOException {
        fileSize = file.length();
        fileName = file.getName();
        this.file = file;
        chunkSize = Settings.chunkSize;
        blockSize = Settings.blockSize;
        readQueue = new ReadQueue(file);

    }

    @Override
    public void run() {
        try (Socket socket = new Socket(Settings.serverAddress, Settings.serverPort);
             DataOutputStream socketOut = new DataOutputStream(socket.getOutputStream());
             DataInputStream socketIn = new DataInputStream(socket.getInputStream());) {

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

            Thread writerThread = new Thread(readQueue);
            writerThread.start();

            System.out.println("Started transfer of file: " + fileName);

            while (true) {
                byte[] byteBlock = readQueue.getNextChunk();
                int blockLength = byteBlock.length;
                if (blockLength == 0) { break; }
                for (int i = 0; blockLength > 0; i += blockSize) {
                    int byteSize = Math.min(blockSize, blockLength); //calc end offset, EOF is smaller
                    socketOut.writeBoolean(false); // Relay EOF = false;
                    socketOut.writeInt(byteSize);
                    socketOut.write(byteBlock, i, byteSize);
                    socketOut.flush();
                    blockLength -= byteSize;
                }
            }

            socketOut.writeBoolean(true); // Relay EOF = true;
            socketOut.flush();
            boolean success = socketIn.readBoolean(); // wait for servers last write, to avoid an exception on quick disconnect

            if (success) {
                System.out.println("Finished transfer for file: " + fileName);
            } else {
                System.out.println("Error during finalization of file transfer");
                Main.activeTransfers.remove(fileName);
                readQueue.stop();
                throw new IllegalStateException("Server responded to end of transfer as failed");
            }
            if (Settings.deleteAfterTransfer) {
                Files.delete(file.toPath());
                System.out.println("Deleted file: " + file);
            }
            readQueue.stop();
            Main.activeTransfers.remove(fileName);

        } catch (IOException e) {
            readQueue.stop();
            Main.activeTransfers.remove(fileName);
            System.out.println("Error in file transfer, most likely connection was lost.");
            e.printStackTrace();
        } catch (Exception e) {
            readQueue.stop();
            Main.activeTransfers.remove(fileName);
            e.printStackTrace();
        }
    }
}
