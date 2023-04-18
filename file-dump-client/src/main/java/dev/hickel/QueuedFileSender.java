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
    private final Socket socket;

    public QueuedFileSender(File file) throws IOException {
        fileSize = file.length();
        fileName = file.getName();
        this.file = file;
        chunkSize = Settings.chunkSize;
        blockSize = Settings.blockSize;
        socket = new Socket(Settings.serverAddress, Settings.serverPort);
        socket.setSoTimeout(60000);
    }

    @Override
    public void run() {
        try (DataOutputStream socketOut = new DataOutputStream(socket.getOutputStream());
             DataInputStream socketIn = new DataInputStream(socket.getInputStream())) {

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

            var total = 0;
            byte[] currBuffer;
            while (!bufferQueue.onePollLeft()) {
                currBuffer = bufferQueue.poll();
                for (int i = 0; i < chunkSize; i += blockSize) {
                    socketOut.writeBoolean(false);
                    socketOut.writeInt(blockSize);
                    socketOut.write(currBuffer, i, blockSize);
                    socketOut.flush();
                    total += blockSize;
                }
                bufferQueue.finishedRead();
            }

            // Last buffer is mostly likely not full, poll and do an extra iteration if needed
            // that is the partial block size
            currBuffer = bufferQueue.poll();
            int lastBuffSize = bufferQueue.lastBytes();
            int sendsLeft =  (lastBuffSize / blockSize) + 1; // if unneeded (32kib exact last block we break);
            for (int i = 0; i < sendsLeft; ++i) {
                int byteSize = i != sendsLeft -1 ? blockSize : lastBuffSize % blockSize;
                if (byteSize == 0) break; // Don't send in the edge case of not needing the last iter
                socketOut.writeBoolean(false);
                socketOut.writeInt(byteSize);
                socketOut.write(currBuffer, i * blockSize, byteSize);
                socketOut.flush();
                total += byteSize;
            }
            System.out.println("total:" + total);
            System.out.println("file:" + fileSize);
            socketOut.writeBoolean(true); // Send EOF
            socketOut.flush();

            boolean success = socketIn.readBoolean(); // wait for servers last write, to avoid an exception on quick disconnect
            socket.close();
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
            bufferQueue.close();
            Main.activeTransfers.remove(fileName);
        } catch (IOException e) {
            Main.activeTransfers.remove(fileName);
            System.out.println("Error in file transfer, most likely connection was lost.");
            e.printStackTrace();
            try { socket.close(); } catch (IOException ee) { System.out.println("Error closing socket"); }

        } catch (Exception e) {
            Main.activeTransfers.remove(fileName);
            e.printStackTrace();
            try { socket.close(); } catch (IOException ee) { System.out.println("Error closing socket"); }
        } finally {
            System.out.println("closed");
            if (socket != null) {
                try { socket.close(); } catch (IOException e) { System.out.println("Error closing socket"); }
            }

        }
    }
}
