package com.harbourmaster.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.harbourmaster.data.BoatSize;
import com.harbourmaster.data.SailingObstacles;
import com.harbourmaster.data.SailingPathfinder;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.api.IndexDataBase;
import net.runelite.api.coords.WorldPoint;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

public final class RouteDataGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OPENRS2 = "https://archive.openrs2.org";
    private static final Path WORKING_DIRECTORY = Path.of("build", "route-generation");

    private RouteDataGenerator() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && "--latest-cache-id".equals(arguments[0])) {
            System.out.println(cache(null).id);
            return;
        }
        String cacheId = arguments.length == 0 || arguments[0].isEmpty() ? null : arguments[0];
        BoatSize boatSize = arguments.length < 2 || arguments[1].isEmpty() ? null : BoatSize.valueOf(arguments[1]);
        CacheRecord cache = cache(cacheId);
        Path cacheDirectory = download(cache);
        System.out.println("Using Old School live cache " + cache.id + " from " + cache.timestamp);
        generate(cacheDirectory, cache.id, boatSize);
    }

    private static CacheRecord cache(String cacheId) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(OPENRS2 + "/caches.json"))
                .GET()
                .build();
        CacheRecord[] records;
        try (InputStream response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream()).body()) {
            records = GSON.fromJson(new InputStreamReader(response, StandardCharsets.UTF_8), CacheRecord[].class);
        }
        if (cacheId != null) {
            for (CacheRecord record : records) {
                if (cacheId.equals(String.valueOf(record.id))) {
                    requireComplete(record);
                    return record;
                }
            }
            throw new IllegalArgumentException("OpenRS2 has no Old School live cache " + cacheId);
        }
        return java.util.Arrays.stream(records)
                .filter(record -> "oldschool".equals(record.game)
                        && "live".equals(record.environment)
                        && "en".equals(record.language)
                        && record.isComplete())
                .max(Comparator.comparing(record -> Instant.parse(record.timestamp)))
                .orElseThrow(() -> new IllegalStateException("OpenRS2 has no complete English live cache"));
    }

    private static void requireComplete(CacheRecord record) {
        if (!"oldschool".equals(record.game)
                || !"live".equals(record.environment)
                || !"en".equals(record.language)
                || !record.isComplete()) {
            throw new IllegalArgumentException("OpenRS2 cache " + record.id + " is not a complete English live cache");
        }
    }

    private static Path download(CacheRecord record) throws IOException, InterruptedException {
        Path directory = WORKING_DIRECTORY.resolve(String.valueOf(record.id));
        Path archive = directory.resolve("disk.zip");
        Path cacheDirectory = directory.resolve("cache");
        Files.createDirectories(directory);
        if (!Files.exists(cacheDirectory.resolve("main_file_cache.dat2"))) {
            if (!Files.exists(archive)) {
                Path partial = directory.resolve("disk.zip.part");
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(OPENRS2 + "/caches/runescape/" + record.id + "/disk.zip"))
                        .GET()
                        .build();
                HttpResponse<Path> response =
                        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofFile(partial));
                if (response.statusCode() != 200) {
                    throw new IOException("OpenRS2 cache download returned HTTP " + response.statusCode());
                }
                Files.move(partial, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            extract(archive, directory);
        }
        return cacheDirectory;
    }

    private static void extract(Path archive, Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(archive);
                ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Invalid path in OpenRS2 cache archive: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (OutputStream output = Files.newOutputStream(target)) {
                    zip.transferTo(output);
                }
            }
        }
    }

    private static void generate(Path cacheDirectory, long cacheId, BoatSize selectedBoatSize) throws Exception {
        long startedAt = System.nanoTime();
        IndexDataBase terrain;
        Map<Integer, BitSet> obstacleRegions;
        try (Store store = new Store(cacheDirectory.toFile())) {
            store.load();
            obstacleRegions = SailingObstacleGenerator.generate(store);
            terrain = new CacheTerrainIndexDataBase(store, store.getIndex(IndexType.MAPS));
        }
        SailingObstacles obstacles = new SailingObstacles(obstacleRegions);
        SailingPathfinder.prepareMasks();
        ExecutorService workers = Executors.newFixedThreadPool(selectedBoatSize == null ? BoatSize.values().length : 1);
        Map<BoatSize, Future<List<RouteEntry>>> generated = new EnumMap<>(BoatSize.class);
        try {
            for (BoatSize boatSize : BoatSize.values()) {
                if (selectedBoatSize != null && selectedBoatSize != boatSize) {
                    continue;
                }
                generated.put(boatSize, workers.submit(() -> generate(terrain, obstacles, boatSize)));
            }
            Map<BoatSize, List<RouteEntry>> completed = new EnumMap<>(BoatSize.class);
            for (Map.Entry<BoatSize, Future<List<RouteEntry>>> entry : generated.entrySet()) {
                completed.put(entry.getKey(), entry.getValue().get());
            }
            SailingObstacleGenerator.write(obstacleRegions);
            for (Map.Entry<BoatSize, List<RouteEntry>> entry : completed.entrySet()) {
                write(entry.getKey(), cacheId, entry.getValue());
            }
        } finally {
            workers.shutdownNow();
        }
        System.out.printf(
                "Generated sailing routes in %.1f minutes%n", (System.nanoTime() - startedAt) / 60_000_000_000.0);
    }

    private static List<RouteEntry> generate(IndexDataBase terrain, SailingObstacles obstacles, BoatSize boatSize) {
        SailingPathfinder pathfinder = new SailingPathfinder(terrain, 1, obstacles);
        List<RouteEntry> routes = new ArrayList<>();
        for (int toIndex = 1; toIndex < Port.values().length; toIndex++) {
            Port to = Port.values()[toIndex];
            pathfinder.prepareDestination(to.navigationLocation, boatSize);
            for (int fromIndex = 0; fromIndex < toIndex; fromIndex++) {
                Port from = Port.values()[fromIndex];
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("Sailing route generation interrupted");
                }
                RouteLeg route = pathfinder
                        .route(from.navigationLocation, to.navigationLocation, boatSize)
                        .map(result -> pathfinder.refineRoute(result, boatSize))
                        .map(result -> new RouteLeg(from, to, result.distance, result.points))
                        .orElse(null);
                routes.add(new RouteEntry(from, to, route));
                System.out.printf(
                        "%s: %s to %s, %s%n",
                        boatSize,
                        from.name,
                        to.name,
                        route == null ? "unreachable" : String.format("%.2f tiles", route.distance));
            }
        }
        routes.sort(Comparator.comparingInt(
                        (RouteEntry route) -> Port.valueOf(route.from).ordinal())
                .thenComparingInt(route -> Port.valueOf(route.to).ordinal()));
        return routes;
    }

    private static final class CacheTerrainIndexDataBase implements IndexDataBase {
        private final Map<Integer, byte[]> regions = new HashMap<>();

        private CacheTerrainIndexDataBase(Store store, Index index) throws IOException {
            for (Archive archive : index.getArchives()) {
                if (!SailingPathfinder.includesRegion(archive.getArchiveId())) {
                    continue;
                }
                ArchiveFiles files = archive.getFiles(store.getStorage().loadArchive(archive));
                regions.put(archive.getArchiveId(), files.findFile(0).getContents());
            }
        }

        @Override
        public boolean isOverlayOutdated() {
            return false;
        }

        @Override
        public int[] getFileIds(int regionId) {
            return regions.containsKey(regionId) ? new int[] {0} : new int[0];
        }

        @Override
        public byte[] loadData(int regionId, int fileId) {
            return fileId == 0 ? regions.get(regionId) : null;
        }
    }

    private static void write(BoatSize boatSize, long cacheId, List<RouteEntry> routes) throws IOException {
        RouteFile file = new RouteFile();
        file.formatVersion = 1;
        file.cacheId = cacheId;
        file.boatSize = boatSize.name();
        file.routes = routes;
        Path output = Path.of(
                "src",
                "main",
                "resources",
                "com",
                "harbourmaster",
                "routes",
                boatSize.name().toLowerCase(Locale.ROOT) + ".json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(file) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static final class CacheRecord {
        private long id;
        private String game;
        private String environment;
        private String language;
        private String timestamp;
        private Integer valid_indexes;
        private Integer indexes;
        private Integer valid_groups;
        private Integer groups;

        @SerializedName("disk_store_valid")
        private Boolean disk_store_valid;

        private boolean isComplete() {
            return Boolean.TRUE.equals(disk_store_valid)
                    && valid_indexes != null
                    && valid_indexes.equals(indexes)
                    && valid_groups != null
                    && valid_groups.equals(groups)
                    && timestamp != null;
        }
    }

    private static final class RouteFile {
        private int formatVersion;
        private long cacheId;
        private String boatSize;
        private List<RouteEntry> routes;
    }

    private static final class RouteEntry {
        private String from;
        private String to;
        private Double distance;
        private int[][] points;

        private RouteEntry(Port from, Port to, RouteLeg route) {
            this.from = from.name();
            this.to = to.name();
            if (route == null) {
                this.points = new int[0][];
                return;
            }
            distance = route.distance;
            points = new int[route.points.size()][2];
            for (int index = 0; index < route.points.size(); index++) {
                WorldPoint point = route.points.get(index);
                points[index] = new int[] {point.getX(), point.getY()};
            }
        }
    }
}
