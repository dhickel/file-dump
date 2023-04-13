package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;


public class FileSender implements Runnable {
    private final Socket socket;
    private final String fileName;
    private final long fileSize;
    private final File file;
    private final int chunkSize;
    private final int blockSize;

    public FileSender(File file) throws IOException {
        fileSize = file.length();
        fileName = file.getName();
        socket = new Socket(Settings.serverAddress, Settings.serverPort);
        socket.setTcpNoDelay(false);
        this.file = file;
        chunkSize = Settings.chunkSize;
        blockSize = Settings.blockSize;
    }

    @Override
    public void run() {
        try (DataOutputStream socketOut = new DataOutputStream(socket.getOutputStream());
             DataInputStream socketIn = new DataInputStream(socket.getInputStream());
             FileInputStream inputFile = new FileInputStream(file)) {

            // Send file info
            socketOut.writeUTF(fileName);
            socketOut.writeLong(fileSize);
            socketOut.flush();

            boolean accepted = socketIn.readBoolean();
            if (!accepted) {
                System.out.println("No free space in server directories. Or file already exists on server.");
                return;
            }
            Main.activeTransfers.add(fileName);
            System.out.println("Started transfer of file: " + fileName);
            byte[] buffer = new byte[chunkSize];

            int bytesRead;
            while ((bytesRead = inputFile.read(buffer)) != -1) {
                for (int i = 0; bytesRead > 0; i+= blockSize) {
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
            socketIn.readBoolean(); // wait for servers last write, to avoid an exception on quick disconnect
            System.out.println("Finished transfer for file: " + fileName);

            if (Settings.deleteAfterTransfer) {
                Files.delete(file.toPath());
                System.out.println("Deleted file: " + file);
            }

        } catch (IOException e) {
            Main.activeTransfers.remove(fileName);
            System.out.println("Error in file transfer, most likely connection was lost.");
            e.printStackTrace();
        }
    }
}
