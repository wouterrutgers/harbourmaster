package com.harbourmaster.data;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class SailingObstacles {
    private final Map<Integer, BitSet> regions;

    public SailingObstacles(Map<Integer, BitSet> regions) {
        this.regions = Map.copyOf(regions);
    }

    public static SailingObstacles load() {
        return Bundled.INSTANCE;
    }

    public void removeFrom(int regionId, BitSet water) {
        BitSet blocked = regions.get(regionId);
        if (blocked != null) {
            water.andNot(blocked);
        }
    }

    private static final class Bundled {
        private static final SailingObstacles INSTANCE = read();

        private static SailingObstacles read() {
            InputStream resource =
                    SailingObstacles.class.getResourceAsStream("/com/harbourmaster/routes/obstacles.dat.gz");
            if (resource == null) {
                throw new IllegalStateException("Missing generated sailing obstacles");
            }
            try (DataInputStream input = new DataInputStream(new GZIPInputStream(resource))) {
                Map<Integer, BitSet> regions = new HashMap<>();
                int count = input.readInt();
                for (int index = 0; index < count; index++) {
                    int regionId = input.readUnsignedShort();
                    byte[] tiles = new byte[512];
                    input.readFully(tiles);
                    regions.put(regionId, BitSet.valueOf(tiles));
                }
                return new SailingObstacles(regions);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read generated sailing obstacles", exception);
            }
        }
    }
}
