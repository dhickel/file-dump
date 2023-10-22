package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.concurrent.locks.LockSupport;


public class FileSender implements Runnable {
    private final String fileName;
    private final long fileSize;
    private final File file;
    private final int chunkSize;
    private final int blockSize;
    private final Socket socket;

    public FileSender(File file, String address, int port) throws IOException {
        fileSize = file.length();
        fileName = file.getName();
        this.file = file;
        chunkSize = Settings.chunkSize;
        blockSize = Settings.blockSize;
        socket = new Socket(address, port);
        socket.setSoTimeout(120_000);
        socket.setTrafficClass(24);
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
                Main.activeTransfers.remove(fileName);
                System.out.println("No space for, file already exists, or all paths in use: " + fileName
                        + " will retry in: " + Settings.fileCheckInterval + " Seconds"
                        + " | Host: " + socket.getInetAddress().getHostAddress());
                return;
            }

            System.out.println("Started transfer of file: " + fileName
                    + "Host: " + socket.getInetAddress().getHostAddress()
            );

            int bytesRead;
            byte[] buffer = new byte[blockSize];
            while ((bytesRead = inputFile.read(buffer, 0, blockSize)) != -1) {
                socketOut.writeInt(bytesRead);
                socketOut.write(buffer, 0, bytesRead);
                socketOut.flush();
            }
            socketOut.writeInt(-1);
            socketOut.flush();

            boolean success = socketIn.readBoolean(); // wait for servers last write, to avoid an exception on quick disconnect
            if (success) {
                System.out.println("Finished transfer for file: " + fileName
                        + "| Host: " + socket.getInetAddress().getHostAddress());
            } else {
                System.out.println("Error during finalization of file transfer");
                Main.activeTransfers.remove(fileName);
                throw new IllegalStateException("Server responded to end of transfer as failed"
                        + " | Host: " + socket.getInetAddress().getHostAddress());
            }
            if (Settings.deleteAfterTransfer) {
                Files.delete(file.toPath());
                System.out.println("Deleted file: " + file);
            }
            Main.activeTransfers.remove(fileName);

        } catch (IOException e) {
            Main.activeTransfers.remove(fileName);
            System.out.println("Error in file transfer, most likely connection was lost."
                    + " | Host: " + socket.getInetAddress().getHostAddress());
            e.printStackTrace();
            try { socket.close(); } catch (IOException ee) {
                System.out.println("Error closing socket" + " | Host: " + socket.getInetAddress().getHostAddress());
            }
        } catch (Exception e) {
            Main.activeTransfers.remove(fileName);
            e.printStackTrace();
            try { socket.close(); } catch (IOException ee) { System.out.println("Error closing socket"
                    + " | Host: " + socket.getInetAddress().getHostAddress());
            }
        } finally {
            System.gc();
            if (socket != null) {
                try { socket.close(); } catch (IOException e) { System.out.println("Error closing socket"
                        + "| Host: " + socket.getInetAddress().getHostAddress());
                }
            }

        }
    }
}
