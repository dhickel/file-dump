package dev.hickel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class Settings {
    public static volatile int maxTransfers = 3;
    public static volatile int blockSize = 32768;
    public static volatile int chunkSize = 4194304;
    public static volatile int fileCheckInterval = 15000;
    public static volatile String serverAddress = "localhost";
    public static volatile int serverPort = 9000;
    public static volatile List<Path> monitoredDirectories = List.of();
    public static volatile List<String> monitoredFileTypes = List.of();
    public static boolean deleteAfterTransfer = false;

    private void writeEmptyConfig() {
        var yaml = new ObjectMapper(new YAMLFactory());
        var userDir = System.getProperty("user.dir") + File.separator;
        var defaultConfig = new File(userDir + "default.yaml");
        try (final FileWriter fw = new FileWriter(defaultConfig)) {
            fw.write(yaml.writeValueAsString(this));
        } catch (IOException e) {
            System.out.println("Failed to write default config.");
            throw new RuntimeException(e);
        }
    }


}
