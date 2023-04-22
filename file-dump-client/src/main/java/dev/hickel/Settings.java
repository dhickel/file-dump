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
    public static volatile List<String> serverAddresses = List.of("localhost");
    public static boolean separateThreadForReading = true;
    public static volatile List<Integer> serverPorts = List.of(9988);
    public static volatile List<Integer> transferSpeedCaps = List.of(-1);
    public static volatile List<Integer> maxTransfers = List.of(3);
    public static volatile int socketBufferSize = 32768;
    public static volatile int readQueueSize = 4;
    public static volatile List<String> monitoredDirectories = List.of();
    public static volatile List<String> monitoredFileTypes = List.of();
    public static volatile int blockSize = 32768;
    public static volatile int chunkSize = 4194304;
    public static volatile int fileCheckInterval = 3;
    public static boolean deleteAfterTransfer = true;
    private static volatile String lastCheckSum = "";
    private static final TypeReference<List<String>> STRING_REF = new TypeReference<>() { };
    private static final TypeReference<List<Integer>> INTEGER_REF = new TypeReference<>() { };

    public static void load() throws IOException {
        String config = System.getProperty("user.dir") + File.separator + "config.yaml";
        File configFile = Path.of(config).toFile();
        String configChecksum = getCheckSum(configFile);
        if (lastCheckSum.equals(configChecksum)) { return; }
        lastCheckSum = configChecksum;

        var mapper = new ObjectMapper(new YAMLFactory());
        JsonNode node = mapper.readTree(configFile);
        var iter = node.fields();
        while (iter.hasNext()) {
            var next = iter.next();
            switch (next.getKey()) {
                case "serverAddresses" -> serverAddresses = mapper.readValue(next.getValue().traverse(), STRING_REF);
                case "separateThreadForReading" -> separateThreadForReading = next.getValue().asBoolean();
                case "serverPorts" -> serverPorts = mapper.readValue(next.getValue().traverse(), INTEGER_REF);
                case "transferSpeedCaps" -> transferSpeedCaps = mapper.readValue(next.getValue().traverse(), INTEGER_REF);
                case "maxTransfers" -> maxTransfers = mapper.readValue(next.getValue().traverse(), INTEGER_REF);
                case "socketBufferSize" -> socketBufferSize = next.getValue().asInt();
                case "readQueueSize" -> readQueueSize = next.getValue().asInt();
                case "monitoredDirectories" ->
                        monitoredDirectories = mapper.readValue(next.getValue().traverse(), STRING_REF);
                case "monitoredFileTypes" ->
                        monitoredFileTypes = mapper.readValue(next.getValue().traverse(), STRING_REF);
                case "blockSize" -> blockSize = next.getValue().asInt();
                case "chunkSize" -> chunkSize = next.getValue().asInt();
                case "fileCheckInterval" -> fileCheckInterval = next.getValue().asInt();
                case "deleteAfterTransfer" -> deleteAfterTransfer = next.getValue().asBoolean();
                default -> System.out.println("Unrecognized field name in config");
            }
        }
        System.out.println(printConfig());
    }

    private static String printConfig() {
        final StringBuilder sb = new StringBuilder("Loaded Config: ");
        sb.append("\n  serverAddresses: ").append(serverAddresses);
        sb.append("\n  separateThreadForReading: ").append(separateThreadForReading);
        sb.append("\n  serverPorts: ").append(serverPorts);
        sb.append("\n  maxTransfers: ").append(maxTransfers);
        sb.append("\n  transferSpeedCaps: ").append(transferSpeedCaps);
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


    private static long secToNanos(double sec) {
        return (long) (sec * 1000000000);
    }

    public static long calcRateLimit(long startNanos, long totalTransferred, int rateMiB) {
        double rateBytesPerSec = (double) rateMiB * 1024 * 1024;
        double elapsedTimeSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        double expectedBytesTransferred = elapsedTimeSec * rateBytesPerSec;
        long sleepTimeNanos = secToNanos((expectedBytesTransferred - totalTransferred) / rateBytesPerSec);
        return Math.max(0, sleepTimeNanos);
    }

}
