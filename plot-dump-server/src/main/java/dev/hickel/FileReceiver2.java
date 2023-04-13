package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;


public class FileReceiver2 implements Runnable {
    private final Socket socket;
    private String fileName = "";
    ByteBuffer buffer = ByteBuffer.allocate(Settings.blockBufferSize);
    long offset = 0;

    public FileReceiver2(Socket socket) {
        this.socket = socket;
    }

    private void writeFile(RandomAccessFile outFile) throws IOException {
        outFile.seek(offset);
        byte[] bufferBytes = buffer.array();
        outFile.write(bufferBytes, 0, buffer.position());
        offset += bufferBytes.length;
        buffer.position(0);
    }

    @Override
    public void run() {
        long fileSize = 0;
        long startTime = System.currentTimeMillis();
        try (DataInputStream socketIn = new DataInputStream(socket.getInputStream());
             DataOutputStream socketOut = new DataOutputStream(socket.getOutputStream())) {

            fileName = socketIn.readUTF();
            fileSize = socketIn.readLong();

            Path outputPath = null;
            Path freePath = Main.activePaths.getPath(fileSize);
            if (freePath != null) {
                outputPath = Paths.get(freePath.toString(), (fileName));
            } else {
                socketOut.writeBoolean(false);
                System.out.println("No free space for file: " + fileName);
                return;
            }
            socketOut.writeBoolean(true);
            System.out.println("Receiving file: " + fileName);

            try (RandomAccessFile outFile = new RandomAccessFile(outputPath.toFile(), "rw")) {
                socketOut.writeBoolean(true);
                while (true) {

                    boolean fin = socketIn.readBoolean();
                    if (fin) {
                        writeFile(outFile); // After print or else try-resources returns when client socket closes
                        long seconds = (System.currentTimeMillis() - startTime) / 1000;
                        String sb = "Finished receiving file: " +
                                fileName +
                                "\tTime: " + seconds + " Sec" +
                                "\tSpeed: " + (double) fileSize / 1048576 / seconds + "MiBs";
                        System.out.println(seconds);
                        System.out.println(sb);
                         socketOut.writeBoolean(true);
                         socketOut.flush();
                        return;
                    }

                    int bytesRead = socketIn.readInt();
                    byte[] b = socketIn.readNBytes(bytesRead);
                    buffer.put(b);
                    if (buffer.position() >= Settings.blockBufferSize) {
                        writeFile(outFile);
                    }
                    socketOut.writeBoolean(true); // continue packet;
                    socketOut.flush();

                }
            }
        } catch (IOException e) {
            System.out.println("Failed to open one or more data streams. Aborting transfer of: " + fileName);
            e.printStackTrace();
        }

    }
}

