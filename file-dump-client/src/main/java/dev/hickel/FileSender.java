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
             DataInputStream socketIn = new DataInputStream(socket.getInputStream());
             FileInputStream inputFile = new FileInputStream(file)) {

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

            System.out.println("Started transfer of file: " + fileName);
            byte[] buffer = new byte[chunkSize];

            int bytesRead;

            while ((bytesRead = inputFile.read(buffer)) != -1) {
                for (int i = 0; bytesRead > 0; i += blockSize) {
                    int byteSize = Math.min(blockSize, bytesRead); //calc end offset, EOF is smaller
                    socketOut.writeBoolean(false); // Relay EOF = false;
                    socketOut.writeInt(byteSize);
                    socketOut.write(buffer, i, byteSize);
                    socketOut.flush();
                    bytesRead -= byteSize;
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
                throw new IllegalStateException("Server responded to end of transfer as failed");
            }
            if (Settings.deleteAfterTransfer) {
                Files.delete(file.toPath());
                System.out.println("Deleted file: " + file);
            }
            Main.activeTransfers.remove(fileName);

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
