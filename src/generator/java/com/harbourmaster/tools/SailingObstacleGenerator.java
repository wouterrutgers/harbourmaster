package com.harbourmaster.tools;

import com.harbourmaster.data.SailingPathfinder;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.LocationsLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;

final class SailingObstacleGenerator {
    private SailingObstacleGenerator() {}

    static Map<Integer, BitSet> generate(Store store) throws IOException {
        if (store.getIndex(IndexType.MAPS).isNamed()) {
            throw new IllegalArgumentException("Sailing obstacles require the current unencrypted map cache format");
        }
        Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.OBJECT.getId());
        ArchiveFiles files = archive.getFiles(store.getStorage().loadArchive(archive));
        Map<Integer, ObjectDefinition> definitions = new TreeMap<>();
        for (var file : files.getFiles()) {
            definitions.put(file.getFileId(), new ObjectLoader().load(file.getFileId(), file.getContents()));
        }
        Map<Integer, BitSet> regions = new TreeMap<>();
        for (Archive region : store.getIndex(IndexType.MAPS).getArchives()) {
            if (!SailingPathfinder.includesRegion(region.getArchiveId())) {
                continue;
            }
            int regionX = region.getArchiveId() >> 8;
            int regionY = region.getArchiveId() & 255;
            ArchiveFiles map = region.getFiles(store.getStorage().loadArchive(region));
            for (Location location : new LocationsLoader()
                    .load(regionX, regionY, map.findFile(1).getContents())
                    .getLocations()) {
                if (location.getPosition().getZ() != 0) {
                    continue;
                }
                ObjectDefinition definition = definitions.get(location.getId());
                int type = location.getType();
                if (definition.getInteractType() == 0
                        || definition.isHollow()
                        || (type >= 4 && type <= 8)
                        || (type == 22 && definition.getInteractType() != 1)) {
                    continue;
                }
                int x = regionX * 64 + location.getPosition().getX();
                int y = regionY * 64 + location.getPosition().getY();
                int rotation = location.getOrientation();
                if (type <= 3) {
                    block(regions, x, y);
                    int[] horizontal = {-1, 0, 1, 0};
                    int[] vertical = {0, 1, 0, -1};
                    block(regions, x + horizontal[rotation], y + vertical[rotation]);
                    if (type != 0) {
                        int next = (rotation + 1) % 4;
                        block(regions, x + horizontal[next], y + vertical[next]);
                    }
                    continue;
                }
                int width = rotation % 2 == 0 ? definition.getSizeX() : definition.getSizeY();
                int height = rotation % 2 == 0 ? definition.getSizeY() : definition.getSizeX();
                for (int horizontal = 0; horizontal < width; horizontal++) {
                    for (int vertical = 0; vertical < height; vertical++) {
                        block(regions, x + horizontal, y + vertical);
                    }
                }
            }
        }
        System.out.println("Generated object obstacles for " + regions.size() + " regions");
        return regions;
    }

    static void write(Map<Integer, BitSet> regions) throws IOException {
        Path output = Path.of("src/main/resources/com/harbourmaster/routes/obstacles.dat.gz");
        try (DataOutputStream stream = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(output)))) {
            stream.writeInt(regions.size());
            for (Map.Entry<Integer, BitSet> region : regions.entrySet()) {
                stream.writeShort(region.getKey());
                stream.write(Arrays.copyOf(region.getValue().toByteArray(), 512));
            }
        }
    }

    private static void block(Map<Integer, BitSet> regions, int x, int y) {
        regions.computeIfAbsent((x >> 6) * 256 + (y >> 6), ignored -> new BitSet(4096))
                .set((x & 63) * 64 + (y & 63));
    }
}
