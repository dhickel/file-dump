package dev.hickel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class Settings {
    public static List<Path> outputDirectories = List.of();
    public static volatile int port = 9988;
    public static volatile int socketBufferSize = 32768;
    public static volatile int blockBufferSize = 4194304;
    public static volatile boolean overwriteExisting = false;
    public static volatile boolean deleteForSpace = false;
    public static volatile List<String> deletedFileTypes = List.of();
    public static volatile List<Path> deletionDirectories = List.of();
    public static volatile long deletionThreshHold = 96636764160L;

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
