package dev.hickel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class Settings {
    public static volatile int writeStyle = 3;
    public static volatile int queueSize = 8;
    public static volatile List<Path> outputDirectories = List.of(Path.of("/mnt/42c5c6f4-3d60-4eda-980e-0da2ac5412b8"));
    public static volatile boolean oneTransferPerDirectory = false;
    public static volatile int port = 9988;
    public static volatile int socketBufferSize = 32768;
    public static volatile int blockBufferSize = 4194304;
    public static volatile int writeBufferSize = 4194304;
    public static volatile boolean deleteForSpace = false;
    public static volatile List<String> deletedFileTypes = List.of();
    public static volatile List<Path> deletionDirectories = List.of();
    public static volatile long deletionThreshHold = Long.MAX_VALUE;
    public static volatile boolean overWriteExisting = true;
    private static volatile String lastCheckSum = "";
    private static final TypeReference<List<String>> TYPE_REF = new TypeReference<>() { };

    public static boolean load() throws IOException {
//        String config = System.getProperty("user.dir") + File.separator + "config.yaml";
//        File configFile = Path.of(config).toFile();
//        String configChecksum = getCheckSum(configFile);
//        if (lastCheckSum.equals(configChecksum)) { return false; }
//        lastCheckSum = configChecksum;
//
//        var mapper = new ObjectMapper(new YAMLFactory());
//        JsonNode node = mapper.readTree(configFile);
//        var iter = node.fields();
//        while (iter.hasNext()) {
//            var next = iter.next();
//            switch (next.getKey()) {
//                case "writeStyle" -> writeStyle = next.getValue().asInt();
//                case "queueSize" -> queueSize = next.getValue().asInt();
//                case "outputDirectories" ->
//                        outputDirectories = stringsToPaths(mapper.readValue(next.getValue().traverse(), TYPE_REF));
//                case "limitOneTransferPerDirectory" -> oneTransferPerDirectory = next.getValue().asBoolean();
//                case "port" -> port = next.getValue().asInt();
//                case "socketBufferSize" -> socketBufferSize = next.getValue().asInt();
//                case "blockBufferSize" -> blockBufferSize = next.getValue().asInt();
//                case "writeBufferSize" -> writeBufferSize = next.getValue().asInt();
//                case "deleteForSpace" -> deleteForSpace = next.getValue().asBoolean();
//                case "deletedFileTypes" -> deletedFileTypes = mapper.readValue(next.getValue().traverse(), TYPE_REF);
//                case "deletionDirectories" ->
//                        deletionDirectories = stringsToPaths(mapper.readValue(next.getValue().traverse(), TYPE_REF));
//                case "deletionThreshHold" -> deletionThreshHold = (long) next.getValue().asInt() * 1048576;
//                case "overWriteExisting" -> overWriteExisting = next.getValue().asBoolean();
//                default -> System.out.println("Unrecognized field name in config");
//            }
//        }
        System.out.println(printConfig());
        return true;
    }

    private static List<Path> stringsToPaths(List<String> strings) {
        List<Path> paths = new ArrayList<>();
        for (var s : strings) {
            if (!String.valueOf(s.charAt(s.length() - 1)).equals(File.separator)) { s += File.separator; }
            Path p = Path.of(s);
            if (Files.exists(p)) {
                paths.add(p);
            } else {
                System.out.println("Path does not exist, skipping: " + p);
            }
        }
        return paths;
    }

    private static String printConfig() {
        final StringBuilder sb = new StringBuilder("Loaded Config: ");
        sb.append("\n  writeStyle: ").append(writeStyle);
        sb.append("\n  queueSize: ").append(queueSize);
        sb.append("\n  outputDirectories: ").append(outputDirectories);
        sb.append("\n  limitOneTransferPerDirectory: ").append(oneTransferPerDirectory);
        sb.append("\n  port: ").append(port);
        sb.append("\n  socketBufferSize: ").append(socketBufferSize);
        sb.append("\n  blockBufferSize: ").append(blockBufferSize);
        sb.append("\n  writeBufferSize: ").append(writeBufferSize);
        sb.append("\n  deleteForSpace: ").append(deleteForSpace);
        sb.append("\n  deletedFileTypes: ").append(deletedFileTypes);
        sb.append("\n  deletionDirectories: ").append(deletionDirectories);
        sb.append("\n  deletionThreshHold: ").append(deletionThreshHold);
        sb.append("\n  overWriteExisting: ").append(overWriteExisting);
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

    // sticking this here it is used for testing/development
    public static String byteCheckSum(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = md.digest(input);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ignored) { }
        return null;
    }
}
