package dev.hickel;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Path;


public class FileReceiver implements Runnable {
    private final Socket socket;
    private final ActivePaths activePaths;
    private final ByteBuffer buffer;
    private String fileName = "";

    public FileReceiver(Socket socket, ActivePaths activePaths) throws SocketException {
        this.socket = socket;
        this.activePaths = activePaths;
        buffer = ByteBuffer.allocate(Settings.blockBufferSize);
        socket.setTcpNoDelay(false);
        if (Settings.socketBufferSize > 0) { socket.setReceiveBufferSize(Settings.socketBufferSize); }
    }

    private void writeFile(BufferedOutputStream outBuffer, boolean isFinished) throws IOException {
        outBuffer.write(buffer.array(), 0, buffer.position());
        buffer.position(0);
        if (isFinished) { outBuffer.flush(); }
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

            // Inform client to begin
            File outputFile = freePath.toFile();
            System.out.println("Receiving file: " + fileName +" to: " + outputFile.getParentFile());
            socketOut.writeBoolean(true);

            try (FileOutputStream outFileStream = new FileOutputStream(outputFile);
                 BufferedOutputStream bufferStream = Settings.writeBufferSize < 0
                         ? new BufferedOutputStream(outFileStream)
                         : new BufferedOutputStream(outFileStream, Settings.writeBufferSize)) {

                while (true) {
                    // Submit any remaining buffer if server is finished sending, close socket and cleanup
                    boolean finished = socketIn.readBoolean();
                    if (finished) {
                        writeFile(bufferStream, true);
                        activePaths.removePath(fileName);
                        File finalFile = new File(outputFile.getParent(), fileName);
                        outputFile.renameTo(finalFile);

                        if (!finalFile.exists() || finalFile.length() != fileSize) {
                            socketOut.writeBoolean(false); // Relay there as an issues
                            throw new IllegalStateException("Output file does not exist");
                        }
                        socketOut.writeBoolean(true); // Relay successful transfer
                        socketOut.flush();

                        long seconds = (System.currentTimeMillis() - startTime) / 1000;
                        String metrics = "Finished receiving file: " + fileName +" to: " + outputFile.getParentFile() +
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
                        writeFile(bufferStream, false);
                    }
                }
            }
        } catch (IOException e) {
            activePaths.removePath(fileName);
            System.out.println("Error encountered aborting transfer of: " + fileName);
            e.printStackTrace();
        } catch (Exception e) {
            activePaths.removePath(fileName);
            e.printStackTrace();
        }
    }
}
