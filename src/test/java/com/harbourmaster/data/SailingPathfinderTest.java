package com.harbourmaster.data;

import static org.junit.Assert.*;

import com.harbourmaster.model.RouteLeg;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.runelite.api.IndexDataBase;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class SailingPathfinderTest {
    @Test
    public void routeStaysOnWaterAndGoesAroundLand() {
        Set<Long> land = Set.of(tile(12, 8), tile(12, 9), tile(12, 10), tile(12, 11), tile(12, 12));
        SailingPathfinder pathfinder = pathfinder(0, 24, 0, 20, Map.of(0, terrain(0, 0, 0, 24, 0, 20, land)));
        WorldPoint from = point(8, 10);
        WorldPoint to = point(16, 10);
        RouteLeg route = pathfinder.route(from, to, BoatSize.RAFT).orElseThrow(AssertionError::new);

        assertEquals(from, route.points.get(0));
        assertEquals(to, route.points.get(route.points.size() - 1));
        assertTrue(route.distance > 8);
        assertTrue(route.distance < 12);
        assertTrue(route.points.stream().anyMatch(point -> point.getY() != 10));
        assertEquals(route.distance, measuredDistance(route.points), 0.00001);
    }

    @Test
    public void routeFromTheMovingBoatUsesItsCurrentTile() {
        Set<Long> land = Set.of(tile(12, 8), tile(12, 9), tile(12, 10), tile(12, 11), tile(12, 12));
        SailingPathfinder pathfinder = pathfinder(0, 24, 0, 20, Map.of(0, terrain(0, 0, 0, 24, 0, 20, land)));
        WorldPoint destination = point(16, 10);
        RouteLeg original =
                pathfinder.route(point(8, 10), destination, BoatSize.RAFT).orElseThrow(AssertionError::new);
        WorldPoint boat = point(14, 7);
        RouteLeg route = pathfinder.route(boat, destination, BoatSize.RAFT).orElseThrow(AssertionError::new);

        assertEquals(boat, route.points.get(0));
        assertEquals(destination, route.points.get(route.points.size() - 1));
        assertTrue(route.distance < original.distance);
    }

    @Test
    public void routeSearchCanAdvanceInSmallSlicesAndMatchesTheSynchronousRoute() {
        SailingPathfinder pathfinder =
                pathfinder(0, 24, 0, 20, Map.of(0, terrain(0, 0, 0, 24, 0, 20, Set.of(tile(12, 10)))));
        WorldPoint from = point(8, 10);
        WorldPoint to = point(16, 10);
        SailingSearch search = pathfinder.search(from, to, BoatSize.RAFT);

        assertFalse(search.advance(1));
        while (!search.advance(1)) {
            // The game thread can yield between these slices.
        }

        RouteLeg slicedRoute = search.result().orElseThrow(AssertionError::new);
        RouteLeg synchronousRoute = pathfinder.route(from, to, BoatSize.RAFT).orElseThrow(AssertionError::new);
        assertEquals(synchronousRoute.distance, slicedRoute.distance, 0.00001);
        assertEquals(synchronousRoute.points, slicedRoute.points);
        assertTrue(search.expandedStates() > 1);
    }

    @Test
    public void liveSearchWaitsForTerrainAndResumesWhenItArrives() {
        Map<Integer, byte[]> regions = new HashMap<>();
        regions.put(0, null);
        SailingPathfinder pathfinder = pathfinder(0, 24, 0, 20, regions);
        SailingSearch search = pathfinder.search(point(8, 10), point(16, 10), BoatSize.RAFT);

        assertFalse(search.advance(Integer.MAX_VALUE));
        regions.put(0, terrain(0, 0, 0, 24, 0, 20, Set.of()));
        assertTrue(search.advance(Integer.MAX_VALUE));

        assertEquals(8, search.result().orElseThrow(AssertionError::new).distance, 0);
    }

    @Test
    public void backgroundSearchLoadsTerrainThroughTheClientThreadInvoker() throws InterruptedException {
        AtomicReference<Thread> indexThread = new AtomicReference<>();
        LinkedBlockingQueue<BooleanSupplier> clientThreadCalls = new LinkedBlockingQueue<>();
        Map<Integer, byte[]> regions = new HashMap<>();
        regions.put(0, null);
        SailingPathfinder pathfinder = new SailingPathfinder(
                new MemoryIndex(regions, indexThread), 0, 24, 0, 20, callback -> clientThreadCalls.add(callback));
        AtomicReference<RouteLeg> route = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                route.set(pathfinder
                        .route(point(8, 10), point(16, 10), BoatSize.RAFT)
                        .orElseThrow(AssertionError::new));
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        worker.start();
        BooleanSupplier loading = clientThreadCalls.poll(5, TimeUnit.SECONDS);
        assertNotNull(loading);
        assertFalse(loading.getAsBoolean());
        regions.put(0, terrain(0, 0, 0, 24, 0, 20, Set.of()));
        clientThreadCalls.add(loading);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (worker.isAlive() && System.nanoTime() < deadline) {
            BooleanSupplier callback = clientThreadCalls.poll(10, TimeUnit.MILLISECONDS);
            if (callback != null && !callback.getAsBoolean()) {
                clientThreadCalls.add(callback);
            }
        }
        worker.join(1000);

        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertTrue(route.get().distance > 0);
        assertEquals(Thread.currentThread(), indexThread.get());
    }

    @Test
    public void routeLoadsWaterAcrossCacheRegionBoundaries() {
        Map<Integer, byte[]> regions = new HashMap<>();
        regions.put(0, terrain(0, 0, 60, 63, 8, 12, Set.of()));
        regions.put(256, terrain(1, 0, 64, 67, 8, 12, Set.of()));
        SailingPathfinder pathfinder = pathfinder(60, 67, 8, 12, regions);
        RouteLeg route =
                pathfinder.route(point(62, 10), point(66, 10), BoatSize.RAFT).orElseThrow(AssertionError::new);

        assertEquals(4, route.distance, 0);
        assertEquals(point(62, 10), route.points.get(0));
        assertEquals(point(66, 10), route.points.get(route.points.size() - 1));
    }

    @Test
    public void routeIsUnavailableWhenLandSeparatesTheWater() {
        Set<Long> land = Set.of(
                tile(12, 0),
                tile(12, 1),
                tile(12, 2),
                tile(12, 3),
                tile(12, 4),
                tile(12, 5),
                tile(12, 6),
                tile(12, 7),
                tile(12, 8),
                tile(12, 9),
                tile(12, 10),
                tile(12, 11),
                tile(12, 12),
                tile(12, 13),
                tile(12, 14),
                tile(12, 15),
                tile(12, 16),
                tile(12, 17),
                tile(12, 18),
                tile(12, 19),
                tile(12, 20));
        SailingPathfinder pathfinder = pathfinder(0, 24, 0, 20, Map.of(0, terrain(0, 0, 0, 24, 0, 20, land)));

        assertFalse(pathfinder.route(point(8, 10), point(16, 10), BoatSize.RAFT).isPresent());
    }

    @Test
    public void largeBoatCannotPassThroughAWaterChannelThatFitsARaft() {
        Set<Long> land = new HashSet<>();
        for (int y = 0; y <= 20; y++) {
            if (y != 10) {
                land.add(tile(15, y));
            }
        }
        SailingPathfinder pathfinder = pathfinder(0, 30, 0, 20, Map.of(0, terrain(0, 0, 0, 30, 0, 20, land)));
        WorldPoint from = point(8, 10);
        WorldPoint to = point(22, 10);

        assertTrue(pathfinder.route(from, to, BoatSize.RAFT).isPresent());
        assertFalse(pathfinder.route(from, to, BoatSize.SLOOP).isPresent());
    }

    private static SailingPathfinder pathfinder(int minX, int maxX, int minY, int maxY, Map<Integer, byte[]> regions) {
        return new SailingPathfinder(new MemoryIndex(regions), minX, maxX, minY, maxY);
    }

    private static byte[] terrain(int regionX, int regionY, int minX, int maxX, int minY, int maxY, Set<Long> land) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            for (int plane = 0; plane < 4; plane++) {
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        int worldX = regionX * 64 + x;
                        int worldY = regionY * 64 + y;
                        boolean water = plane == 0
                                && worldX >= minX
                                && worldX <= maxX
                                && worldY >= minY
                                && worldY <= maxY
                                && !land.contains(tile(worldX, worldY));
                        if (water) {
                            output.writeShort(2);
                            output.writeShort(454);
                        }
                        output.writeShort(0);
                    }
                }
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static long tile(int x, int y) {
        return (long) x << 32 | y & 0xffffffffL;
    }

    private static WorldPoint point(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    private static double measuredDistance(List<WorldPoint> points) {
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            WorldPoint from = points.get(index - 1);
            WorldPoint to = points.get(index);
            distance += Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
        }
        return distance;
    }

    private static final class MemoryIndex implements IndexDataBase {
        private final Map<Integer, byte[]> regions;
        private final AtomicReference<Thread> indexThread;

        private MemoryIndex(Map<Integer, byte[]> regions) {
            this(regions, null);
        }

        private MemoryIndex(Map<Integer, byte[]> regions, AtomicReference<Thread> indexThread) {
            this.regions = regions;
            this.indexThread = indexThread;
        }

        @Override
        public boolean isOverlayOutdated() {
            return false;
        }

        @Override
        public int[] getFileIds(int archiveId) {
            return regions.containsKey(archiveId) ? new int[] {0} : new int[0];
        }

        @Override
        public byte[] loadData(int archiveID, int fileID) {
            if (indexThread != null) {
                indexThread.set(Thread.currentThread());
            }
            return fileID == 0 ? regions.get(archiveID) : null;
        }
    }
}
