package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;


public class FileReceiver implements Runnable {
    private final Socket socket;
    private final ByteBuffer buffer;
    private String fileName = "";
    private long offset = 0;
    private volatile boolean stop = false;

    public FileReceiver(Socket socket) throws SocketException {
        this.socket = socket;
        buffer = ByteBuffer.allocate(Settings.blockBufferSize);
        socket.setTcpNoDelay(false);
        if (Settings.socketBufferSize > 0) {
            socket.setReceiveBufferSize(Settings.socketBufferSize);
        }
    }

    private void writeFile(RandomAccessFile outFile) throws IOException {
        outFile.seek(offset);
        outFile.write(buffer.array(), 0, buffer.position());
        offset += buffer.position();
        buffer.position(0);
    }

    private File getFileLocation(String fileName, long fileSize) {
        Path freePath = Main.activePaths.getPath(fileName, fileSize);
        if (freePath == null) {
            return null;
        }
        freePath = freePath.resolve(fileName);
        if (freePath.toFile().exists() && !Settings.overWriteExisting) {
            return null;
        }
        return new File(freePath + ".tmp");
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
            File outputFile = getFileLocation(fileName, fileSize);
            if (outputFile == null) {
                socketOut.writeBoolean(false);
                socketOut.flush();
                System.out.println("No space for, file already exists, or all paths in use: " + fileName);
                return;
            }

            // Inform client to begin
            System.out.println("Receiving file: " + fileName);
            socketOut.writeBoolean(true);
            Main.activeTransfers.put(fileName, outputFile.getParentFile().toPath());

            try (RandomAccessFile outFile = new RandomAccessFile(outputFile, "rw")) {
                while (true) {
                    // Let server know we are not blocking for a write, so it doesn't fill os network buffer
                    socketOut.writeBoolean(true);

                    // Submit any remaining buffer if server is finished sending, close socket and cleanup
                    boolean finished = socketIn.readBoolean();
                    if (finished) {
                        writeFile(outFile);
                        outFile.close();
                        File finalFile = new File(outputFile.getParent(), fileName);
                        outputFile.renameTo(finalFile);
                        if (!finalFile.exists() || finalFile.length() != fileSize) {
                            socketOut.writeBoolean(false); // Relay there as an issues
                            throw new IllegalStateException("Output file does not exist");
                        }
                        socketOut.writeBoolean(true); // Relay successful transfer
                        socketOut.flush();
                        Main.activeTransfers.remove(fileName);

                        long seconds = (System.currentTimeMillis() - startTime) / 1000;
                        String metrics = "Finished receiving file: " + fileName +
                                "\tTime: " + seconds + " Sec" +
                                "\tSpeed: " + Math.round((double) fileSize / 1048576 / seconds) + " MiBs";
                        System.out.println(metrics);
                        return;
                    }

                    // Read buffer
                    int bytesRead = socketIn.readInt();
                    byte[] bytesIn = socketIn.readNBytes(bytesRead);
                    buffer.put(bytesIn);

                    //Wait until buffer reaches desired chunkSize then write to disk
                    if (buffer.position() >= buffer.capacity()) {
                        writeFile(outFile);
                    }
                }
            }
        } catch (IOException e) {
            Main.activeTransfers.remove(fileName);
            System.out.println("Error encountered aborting transfer of: " + fileName);
            e.printStackTrace();
        } catch (Exception e) {
            Main.activeTransfers.remove(fileName);
            e.printStackTrace();
        }
    }
}
