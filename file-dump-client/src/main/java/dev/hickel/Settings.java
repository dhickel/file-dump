package dev.hickel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;


public class Settings {
    public static volatile boolean separateThreadForReading = true;
    public static volatile String serverAddress = "localhost";
    public static volatile int serverPort = 9988;
    public static volatile int maxTransfers = 3;
    public static volatile int socketBufferSize = -1;
    public static volatile int readQueueSize = 1024;
    public static volatile List<String> monitoredDirectories = List.of();
    public static volatile List<String> monitoredFileTypes = List.of();
    public static volatile int blockSize = 32768;
    public static volatile int chunkSize = 4194304;
    public static volatile int fileCheckInterval = 30;
    public static boolean deleteAfterTransfer = false;
    private static volatile String lastCheckSum = "";
    private static final TypeReference<List<String>> TYPE_REF = new TypeReference<>() { };

    public static void load() throws IOException {
//        String config = System.getProperty("user.dir") + File.separator + "config.yaml";
//        File configFile = Path.of(config).toFile();
//        String configChecksum = getCheckSum(configFile);
//        if (lastCheckSum.equals(configChecksum)) { return; }
//        lastCheckSum = configChecksum;
//
//        var mapper = new ObjectMapper(new YAMLFactory());
//        JsonNode node = mapper.readTree(configFile);
//        var iter = node.fields();
//        while (iter.hasNext()) {
//            var next = iter.next();
//            switch (next.getKey()) {
//                case "separateThreadForReading" -> separateThreadForReading = next.getValue().asBoolean();
//                case "serverAddress" -> serverAddress = next.getValue().asText();
//                case "serverPort" -> serverPort = next.getValue().asInt();
//                case "maxTransfers" -> maxTransfers = next.getValue().asInt();
//                case "socketBufferSize" -> socketBufferSize = next.getValue().asInt();
//                case "readQueueSize" -> readQueueSize = next.getValue().asInt();
//                case "monitoredDirectories" ->
//                        monitoredDirectories = mapper.readValue(next.getValue().traverse(), TYPE_REF);
//                case "monitoredFileTypes" ->
//                        monitoredFileTypes = mapper.readValue(next.getValue().traverse(), TYPE_REF);
//                case "blockSize" -> blockSize = next.getValue().asInt();
//                case "chunkSize" -> chunkSize = next.getValue().asInt();
//                case "fileCheckInterval" -> fileCheckInterval = next.getValue().asInt();
//                case "deleteAfterTransfer" -> deleteAfterTransfer = next.getValue().asBoolean();
//                default -> System.out.println("Unrecognized field name in config");
//            }
//        }
        //System.out.println(printConfig());
    }

    private static String printConfig() {
        final StringBuilder sb = new StringBuilder("Loaded Config: ");
        sb.append("\n  separateThreadForReading: ").append(separateThreadForReading);
        sb.append("\n  serverAddress: ").append(serverAddress);
        sb.append("\n  serverPort: ").append(serverPort);
        sb.append("\n  maxTransfers: ").append(maxTransfers);
        sb.append("\n  socketBufferSize: ").append(socketBufferSize);
        sb.append("\n  readQueueSize: ").append(readQueueSize);
        sb.append("\n  monitoredDirectories: ").append(monitoredDirectories);
        sb.append("\n  monitoredFileTypes: ").append(monitoredFileTypes);
        sb.append("\n  blockSize: ").append(blockSize);
        sb.append("\n  chunkSize: ").append(chunkSize);
        sb.append("\n  fileCheckInterval: ").append(fileCheckInterval);
        sb.append("\n  deleteAfterTransfer: ").append(deleteAfterTransfer);
        sb.append("\n");
        return sb.toString();
    }

    public static String getCheckSum(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            int numRead;
            while ((numRead = input.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }

            byte[] digest = md5.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ignored) { }
        return "";
    }
}
