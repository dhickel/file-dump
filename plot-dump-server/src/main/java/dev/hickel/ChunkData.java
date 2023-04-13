package dev.hickel;

public class ChunkData {
    public final byte[] data;
    public final long offset;
    public boolean isLast;
    public ChunkData(byte[] data, long offset, boolean isLast) {
        this.data = data;
        this.offset = offset;
        this.isLast = isLast;
    }
}
