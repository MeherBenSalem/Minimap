package io.nightbeam.remnantminimaps.core.io;

import io.nightbeam.remnantminimaps.core.map.RegionData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * On-disk format of one map region:
 * <pre>
 * int magic 'RMR1', int version, int rx, int rz, long savedAt, int styleStamp
 * int thumbBytes, deflate(64*64 ARGB ints)       -- readable without touching the column data
 * int dataBytes,  deflate(colors[], heights[], depths[])
 * </pre>
 */
public final class RegionFile {
    public static final int MAGIC = 0x524D5231;
    public static final int VERSION = 1;

    private RegionFile() {}

    public record Snapshot(int rx, int rz, int[] colors, short[] heights, byte[] depths, int[] thumbnail, int styleStamp) {
        public static Snapshot of(RegionData region) {
            return new Snapshot(region.rx, region.rz,
                    region.colors.clone(), region.heights.clone(), region.depths.clone(),
                    region.thumbnail().clone(), region.styleStamp);
        }
    }

    public record Thumbnail(int rx, int rz, int[] pixels, int styleStamp) {}

    public static String fileName(int rx, int rz) {
        return "r." + rx + "." + rz + ".rmr";
    }

    public static void write(Path file, Snapshot snapshot) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(snapshot.rx());
            out.writeInt(snapshot.rz());
            out.writeLong(System.currentTimeMillis());
            out.writeInt(snapshot.styleStamp());

            byte[] thumb = deflate(intsToBytes(snapshot.thumbnail()));
            out.writeInt(thumb.length);
            out.write(thumb);

            ByteBuffer raw = ByteBuffer.allocate(RegionData.AREA * 7);
            raw.asIntBuffer().put(snapshot.colors());
            raw.position(RegionData.AREA * 4);
            for (short h : snapshot.heights()) {
                raw.putShort(h);
            }
            raw.put(snapshot.depths());
            byte[] data = deflate(raw.array());
            out.writeInt(data.length);
            out.write(data);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Snapshot read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int[] header = readHeader(in, file);
            int[] thumb = bytesToInts(inflate(readBlock(in), RegionData.THUMB_SIZE * RegionData.THUMB_SIZE * 4));
            ByteBuffer raw = ByteBuffer.wrap(inflate(readBlock(in), RegionData.AREA * 7));
            int[] colors = new int[RegionData.AREA];
            short[] heights = new short[RegionData.AREA];
            byte[] depths = new byte[RegionData.AREA];
            raw.asIntBuffer().get(colors);
            raw.position(RegionData.AREA * 4);
            for (int i = 0; i < RegionData.AREA; i++) {
                heights[i] = raw.getShort();
            }
            raw.get(depths);
            return new Snapshot(header[0], header[1], colors, heights, depths, thumb, header[2]);
        }
    }

    public static Thumbnail readThumbnail(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 32768))) {
            int[] header = readHeader(in, file);
            int[] thumb = bytesToInts(inflate(readBlock(in), RegionData.THUMB_SIZE * RegionData.THUMB_SIZE * 4));
            return new Thumbnail(header[0], header[1], thumb, header[2]);
        }
    }

    private static int[] readHeader(DataInputStream in, Path file) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a Remnants Minimap region: " + file);
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported region version " + version + " in " + file);
        }
        int rx = in.readInt();
        int rz = in.readInt();
        in.readLong();
        int stamp = in.readInt();
        return new int[]{rx, rz, stamp};
    }

    private static byte[] readBlock(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 64 * 1024 * 1024) {
            throw new IOException("Corrupt region block length " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(data.length / 4);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (OutputStream out = new DeflaterOutputStream(bytes, deflater, 8192)) {
            out.write(data);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    private static byte[] inflate(byte[] data, int expected) throws IOException {
        byte[] out = new byte[expected];
        try (InputStream in = new InflaterInputStream(new java.io.ByteArrayInputStream(data))) {
            int read = 0;
            while (read < expected) {
                int n = in.read(out, read, expected - read);
                if (n < 0) {
                    throw new IOException("Truncated region block");
                }
                read += n;
            }
        }
        return out;
    }

    private static byte[] intsToBytes(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * 4);
        buffer.asIntBuffer().put(ints);
        return buffer.array();
    }

    private static int[] bytesToInts(byte[] bytes) {
        int[] ints = new int[bytes.length / 4];
        ByteBuffer.wrap(bytes).asIntBuffer().get(ints);
        return ints;
    }
}
