package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class FileReceiver implements Runnable {
    private final Socket socket;
    private final ByteBuffer buffer;
    private String fileName = "";
    private long offset = 0;

    public FileReceiver(Socket socket) throws SocketException {
        this.socket = socket;
        buffer = ByteBuffer.allocate(Settings.blockBufferSize);
        socket.setTcpNoDelay(false);
        socket.setReceiveBufferSize(Settings.socketBufferSize);
    }

    private void writeFile(RandomAccessFile outFile) throws IOException {
        outFile.seek(offset);
        byte[] bufferBytes = buffer.array();
        outFile.write(bufferBytes, 0, buffer.position());
        offset += bufferBytes.length;
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
            if (freePath != null && !Files.exists(Path.of(freePath + File.separator + fileName))) {
                outputPath = Paths.get(freePath.toString(), fileName + ".tmp");
            } else {
                socketOut.writeBoolean(false);
                System.out.println("No free space for file: " + fileName);
                return;
            }

            System.out.println("Receiving file: " + fileName);
            socketOut.writeBoolean(true);
            Main.activeTransfers.put(fileName, freePath);

            try (RandomAccessFile outFile = new RandomAccessFile(outputPath.toFile(), "rw")) {
                while (true) {
                    boolean fin = socketIn.readBoolean();
                    if (fin) {
                        writeFile(outFile); //Write anything left in buffer
                        outFile.close();
                        outputPath.toFile().renameTo(new File(outputPath.getParent().toFile(), fileName));
                        socketOut.writeBoolean(true); // Relay successful transfer
                        socketOut.flush();
                        long seconds = (System.currentTimeMillis() - startTime) / 1000;
                        String sb = "Finished receiving file: " + fileName +
                                "\tTime: " + seconds + " Sec" +
                                "\tSpeed: " + (double) fileSize / 1048576 / seconds + "MiBs";
                        System.out.println(sb);
                        Main.activeTransfers.remove(fileName);
                        return;
                    }

                    int bytesRead = socketIn.readInt();
                    byte[] bytesIn = socketIn.readNBytes(bytesRead);
                    buffer.put(bytesIn);

                    //Wait until buffer reaches desired chunkSize then write to disk
                    if (buffer.position() >= buffer.capacity()) {
                        writeFile(outFile);
                    }
                }
            } catch (IOException e) {
                Main.activeTransfers.remove(fileName);
                System.out.println("Failed to get file handle. Aborting transfer of: " + fileName);
                e.printStackTrace();
            }
        } catch (IOException e) {
            Main.activeTransfers.remove(fileName);
            System.out.println("Error in dataStream or dropped connection. Aborting transfer of: " + fileName);
            e.printStackTrace();
        }

    }
}