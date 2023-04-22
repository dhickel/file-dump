package dev.hickel;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDate;


public class TransferStats {
    private static final RandomAccessFile randomAccessFile;
    private static String currentDate;
    private static long currentOffset;

    static {
        File file = new File("transfer_stats.csv");
        RandomAccessFile raf = null;

        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            raf = new RandomAccessFile(file, "rw");
            init(raf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        randomAccessFile = raf;
    }

    private static void init(RandomAccessFile raf) throws IOException {
        currentDate = LocalDate.now().toString();
        boolean found = false;
        long lineOffset = 0;

        String line;
        while ((line = raf.readLine()) != null) {
            String date = line.split(",")[0];

            if (date.equals(currentDate)) {
                found = true;
                currentOffset = lineOffset;
                break;
            }

            lineOffset = raf.getFilePointer();
        }

        if (!found) {
            raf.seek(raf.length());
            raf.writeBytes(currentDate + ",0,0\n");
            currentOffset = raf.length() - (currentDate + ",0,0\n").length();
        }
    }

    public static int[] incStats(long dataTransferred) {
        String today = LocalDate.now().toString();
        int dataSize = (int) bytesToGib(dataTransferred);
        try {
            if (!today.equals(currentDate)) {
                currentDate = today;
                randomAccessFile.seek(randomAccessFile.length());
                randomAccessFile.writeBytes(today + ",1," + dataSize + "\n");
                currentOffset = randomAccessFile.length() - (today + ",1," + dataSize + "\n").length();
            } else {
                randomAccessFile.seek(currentOffset);
                String line = randomAccessFile.readLine();
                String[] parts = line.split(",");
                int count = Integer.parseInt(parts[1]) + 1;
                int totalData = Integer.parseInt(parts[2]) + dataSize;
                String updatedLine = currentDate + "," + count + "," + totalData + "\n";
                randomAccessFile.seek(currentOffset);
                randomAccessFile.writeBytes(updatedLine);
                return new int[]{count, totalData};
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new int[]{0, 0};
    }

    private static double bytesToGib(long bytes) {
        return bytes / (1024.0 * 1024.0 * 1024.0);
    }
}