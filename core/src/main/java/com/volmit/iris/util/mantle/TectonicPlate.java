/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.mantle;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.EnginePanic;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.CountingDataInputStream;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.zip.GZIPInputStream;

/**
 * Tectonic Plates are essentially representations of regions in minecraft.
 * Tectonic Plates are fully atomic & thread safe
 */
public class TectonicPlate {
    private static final ThreadLocal<Boolean> errors = ThreadLocal.withInitial(() -> false);
    public static final int MISSING = -1;
    public static final int CURRENT = 0;

    private final int sectionHeight;
    private final AtomicReferenceArray<MantleChunk> chunks;

    @Getter
    private final int x;

    @Getter
    private final int z;

    /**
     * Create a new tectonic plate
     *
     * @param worldHeight the height of the world
     */
    public TectonicPlate(int worldHeight, int x, int z) {
        this.sectionHeight = worldHeight >> 4;
        this.chunks = new AtomicReferenceArray<>(1024);
        this.x = x;
        this.z = z;
    }

    /**
     * Load a tectonic plate from a data stream
     *
     * @param worldHeight the height of the world
     * @param din         the data input
     * @throws IOException            shit happens yo
     * @throws ClassNotFoundException real shit bro
     */
    public TectonicPlate(int worldHeight, DataInputStream din) throws IOException, ClassNotFoundException {
        this(worldHeight, din.readInt(), din.readInt());
        for (int i = 0; i < chunks.length(); i++) {
            if (din.readBoolean()) {
                Iris.addPanic("read-chunk", "Chunk[" + i + "]");
                chunks.set(i, new MantleChunk(sectionHeight, din));
                EnginePanic.saveLast();
            }
        }
    }

    public static TectonicPlate read_old(int worldHeight, File file) throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(file);
        DataInputStream din;
        if (file.getName().endsWith("ttp.lz4b")) {
            LZ4BlockInputStream lz4 = new LZ4BlockInputStream(fin);
            din = new DataInputStream(new BufferedInputStream(lz4));
        } else {
            GZIPInputStream gzi = new GZIPInputStream(fin);
            din = new DataInputStream(new BufferedInputStream(gzi));
        }
        TectonicPlate p = new TectonicPlate(worldHeight, din);
        din.close();

        return p;
    }

    /**
     * Load a tectonic plate from a data stream
     *
     * @param worldHeight the height of the world
     * @param din         the data input
     * @throws IOException            shit happens yo
     */
    public TectonicPlate(int worldHeight, CountingDataInputStream din, boolean versioned) throws IOException, RuntimeException {
        this(worldHeight, din.readInt(), din.readInt());
        if (!din.markSupported())
            throw new IOException("Mark not supported!");

        int v = versioned ? Varint.readUnsignedVarInt(din) : MISSING;
        for (int i = 0; i < chunks.length(); i++) {
            long size = din.readInt();
            if (size == 0) continue;
            long start = din.count();

            try {
                Iris.addPanic("read-chunk", "Chunk[" + i + "]");
                chunks.set(i, new MantleChunk(v, sectionHeight, din));
                EnginePanic.saveLast();
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            } catch (Throwable e) {
                long end = start + size;
                Iris.error("Failed to read chunk, creating a new chunk instead.");
                Iris.addPanic("read.byte.range", start + " " + end);
                Iris.addPanic("read.byte.current", din.count() + "");
                Iris.reportError(e);
                e.printStackTrace();
                Iris.panic();

                din.skipTo(end);
                TectonicPlate.addError();
            }
        }
    }

    public static TectonicPlate read(int worldHeight, File file, boolean versioned) throws IOException, RuntimeException {
        try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
            fc.lock();

            InputStream fin = Channels.newInputStream(fc);
            LZ4BlockInputStream lz4 = new LZ4BlockInputStream(fin);
            BufferedInputStream bis = new BufferedInputStream(lz4);
            try (CountingDataInputStream din = CountingDataInputStream.wrap(bis)) {
                return new TectonicPlate(worldHeight, din, versioned);
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            }
        } finally {
            if (IrisSettings.get().getGeneral().isDumpMantleOnError() && errors.get()) {
                File dump = Iris.instance.getDataFolder("dump", file.getName() + ".bin");
                try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
                    fc.lock();

                    InputStream fin = Channels.newInputStream(fc);
                    LZ4BlockInputStream lz4 = new LZ4BlockInputStream(fin);
                    Files.copy(lz4, dump.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            errors.remove();
        }
    }

    public boolean inUse() {
        for (int i = 0; i < chunks.length(); i++) {
            MantleChunk chunk = chunks.get(i);
            if (chunk != null && chunk.inUse())
                return true;
        }
        return false;
    }

    public void close() throws InterruptedException {
        for (int i = 0; i < chunks.length(); i++) {
            MantleChunk chunk = chunks.get(i);
            if (chunk != null) {
                chunk.close();
            }
        }
    }

    /**
     * Check if a chunk exists in this plate or not (same as get(x, z) != null)
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     * @return true if the chunk exists
     */
    @ChunkCoordinates
    public boolean exists(int x, int z) {
        return get(x, z) != null;
    }

    /**
     * Get a chunk at the given coordinates or null if it doesnt exist
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     * @return the chunk or null if it doesnt exist
     */
    @ChunkCoordinates
    public MantleChunk get(int x, int z) {
        return chunks.get(index(x, z));
    }

    /**
     * Clear all chunks from this tectonic plate
     */
    public void clear() {
        for (int i = 0; i < chunks.length(); i++) {
            chunks.set(i, null);
        }
    }

    /**
     * Delete a chunk from this tectonic plate
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     */
    @ChunkCoordinates
    public void delete(int x, int z) {
        chunks.set(index(x, z), null);
    }

    /**
     * Get a tectonic plate, or create one and insert it & return it if it diddnt exist
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     * @return the chunk (read or created & inserted)
     */
    @ChunkCoordinates
    public MantleChunk getOrCreate(int x, int z) {
        return chunks.updateAndGet(index(x, z), chunk -> {
            if (chunk != null) return chunk;
            return new MantleChunk(sectionHeight, x & 31, z & 31);
        });
    }

    @ChunkCoordinates
    private int index(int x, int z) {
        return Cache.to1D(x, z, 0, 32, 32);
    }

    /**
     * Write this tectonic plate to file
     *
     * @param file the file to writeNodeData it to
     * @throws IOException shit happens
     */
    public void write(File file) throws IOException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        IO.write(file, out -> new DataOutputStream(new LZ4BlockOutputStream(out)), this::write);
        Iris.debug("Saved Tectonic Plate " + C.DARK_GREEN + file.getName() + C.RED + " in " + Form.duration(p.getMilliseconds(), 2));
    }

    /**
     * Write this tectonic plate to a data stream
     *
     * @param dos the data output
     * @throws IOException shit happens
     */
    public void write(DataOutputStream dos) throws IOException {
        dos.writeInt(x);
        dos.writeInt(z);
        Varint.writeUnsignedVarInt(CURRENT, dos);

        var bytes = new ByteArrayOutputStream(8192);
        var sub = new DataOutputStream(bytes);
        for (int i = 0; i < chunks.length(); i++) {
            MantleChunk chunk = chunks.get(i);

            if (chunk != null) {
                try {
                    chunk.write(sub);
                    dos.writeInt(bytes.size());
                    bytes.writeTo(dos);
                } finally {
                    bytes.reset();
                }
            } else {
                dos.writeInt(0);
            }
        }
    }

    public static void addError() {
        errors.set(true);
    }
}
