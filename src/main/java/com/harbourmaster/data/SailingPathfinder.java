package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.IndexDataBase;
import net.runelite.api.coords.WorldPoint;

public final class SailingPathfinder implements SailingRouter {
    public static final int MAP_INDEX_ID = 5;

    private static final int REGION_LENGTH = 64;
    private static final int REGION_TILE_COUNT = REGION_LENGTH * REGION_LENGTH;
    private static final int BOUNDS_MARGIN = 256;
    private static final int DIRECTION_COUNT = 8;
    private static final int HEADING_COUNT = 360;
    private static final int DEGREES_PER_DIRECTION = HEADING_COUNT / DIRECTION_COUNT;
    private static final int HEADING_STATES = DIRECTION_COUNT + 1;
    private static final int INITIAL_HEADING = DIRECTION_COUNT;
    private static final double LIVE_HEURISTIC_WEIGHT = 2;
    private static final Set<Integer> WATER_OVERLAYS =
            Set.of(442, 445, 448, 451, 454, 457, 460, 463, 466, 469, 565, 568, 571, 574, 577);
    private static final int[] HORIZONTAL = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] VERTICAL = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final Map<Integer, List<Offset>> FOOTPRINTS = footprintMasks();
    private static final Map<Integer, List<Offset>> TURNS = turnMasks();
    private static final Map<Integer, List<Offset>> MOVES = moveMasks();

    private final IndexDataBase mapIndex;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int height;
    private final double heuristicWeight;
    private final ConcurrentMap<Integer, CompletableFuture<BitSet>> waterByRegion = new ConcurrentHashMap<>();
    private final Consumer<BooleanSupplier> clientThreadInvoker;
    private final Thread clientThreadOwner;

    public SailingPathfinder(IndexDataBase mapIndex) {
        this(mapIndex, BooleanSupplier::getAsBoolean);
    }

    public SailingPathfinder(IndexDataBase mapIndex, Consumer<BooleanSupplier> clientThreadInvoker) {
        this(mapIndex, clientThreadInvoker, LIVE_HEURISTIC_WEIGHT);
    }

    public SailingPathfinder(IndexDataBase mapIndex, double heuristicWeight) {
        this(mapIndex, BooleanSupplier::getAsBoolean, heuristicWeight);
    }

    private SailingPathfinder(
            IndexDataBase mapIndex, Consumer<BooleanSupplier> clientThreadInvoker, double heuristicWeight) {
        this(
                mapIndex,
                minimumPortCoordinate(true) - BOUNDS_MARGIN,
                maximumPortCoordinate(true) + BOUNDS_MARGIN,
                minimumPortCoordinate(false) - BOUNDS_MARGIN,
                maximumPortCoordinate(false) + BOUNDS_MARGIN,
                clientThreadInvoker,
                heuristicWeight);
    }

    SailingPathfinder(IndexDataBase mapIndex, int minX, int maxX, int minY, int maxY) {
        this(mapIndex, minX, maxX, minY, maxY, BooleanSupplier::getAsBoolean);
    }

    SailingPathfinder(
            IndexDataBase mapIndex,
            int minX,
            int maxX,
            int minY,
            int maxY,
            Consumer<BooleanSupplier> clientThreadInvoker) {
        this(mapIndex, minX, maxX, minY, maxY, clientThreadInvoker, LIVE_HEURISTIC_WEIGHT);
    }

    private SailingPathfinder(
            IndexDataBase mapIndex,
            int minX,
            int maxX,
            int minY,
            int maxY,
            Consumer<BooleanSupplier> clientThreadInvoker,
            double heuristicWeight) {
        this.mapIndex = mapIndex;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.height = maxY - minY + 1;
        this.clientThreadInvoker = clientThreadInvoker;
        this.clientThreadOwner = Thread.currentThread();
        this.heuristicWeight = heuristicWeight;
    }

    @Override
    public Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize boatSize) {
        SailingSearch search = search(from, to, boatSize);
        while (!search.advance(Integer.MAX_VALUE)) {
            // A synchronous caller keeps advancing until the search finishes.
        }
        return search.result();
    }

    @Override
    public SailingSearch search(WorldPoint from, WorldPoint to, BoatSize boatSize) {
        if (from.getPlane() != 0
                || to.getPlane() != 0
                || !insideBounds(from.getX(), from.getY())
                || !insideBounds(to.getX(), to.getY())) {
            return new CompletedSearch(Optional.empty());
        }
        if (from.equals(to)) {
            return new CompletedSearch(Optional.of(new RouteLeg(null, null, 0, List.of(from))));
        }

        int start = key(from.getX(), from.getY());
        int destination = key(to.getX(), to.getY());
        return new PathSearch(from, to, boatSize, start, destination);
    }

    public static int prepareMasks() {
        return FOOTPRINTS.size() + TURNS.size() + MOVES.size();
    }

    private boolean turnFits(
            int tile, int previousHeading, int nextHeading, BoatSize boatSize, int start, int destination) {
        if (previousHeading == INITIAL_HEADING || previousHeading == nextHeading) {
            return true;
        }

        int clockwiseSteps = (nextHeading - previousHeading + DIRECTION_COUNT) % DIRECTION_COUNT;
        if (clockwiseSteps == DIRECTION_COUNT / 2) {
            return turnFits(tile, previousHeading, nextHeading, boatSize, start, destination, 1)
                    || turnFits(tile, previousHeading, nextHeading, boatSize, start, destination, -1);
        }
        return turnFits(
                tile,
                previousHeading,
                nextHeading,
                boatSize,
                start,
                destination,
                clockwiseSteps < DIRECTION_COUNT / 2 ? 1 : -1);
    }

    private boolean turnFits(
            int tile,
            int previousHeading,
            int nextHeading,
            BoatSize boatSize,
            int start,
            int destination,
            int turnDirection) {
        int x = x(tile);
        int y = y(tile);
        for (Offset offset : TURNS.get(turnKey(boatSize, previousHeading, nextHeading, turnDirection))) {
            if (!navigable(x + offset.horizontal, y + offset.vertical, start, destination)) {
                return false;
            }
        }
        return true;
    }

    private boolean moveFits(int x, int y, int direction, BoatSize boatSize, int start, int destination) {
        for (Offset offset : MOVES.get(boatSize.ordinal() * DIRECTION_COUNT + direction)) {
            if (!navigable(x + offset.horizontal, y + offset.vertical, start, destination)) {
                return false;
            }
        }
        return true;
    }

    private List<Integer> path(int start, int destination, Map<Integer, Integer> previous) {
        List<Integer> path = new ArrayList<>();
        for (int state = destination; state != start; state = previous.get(state)) {
            path.add(tile(state));
        }
        path.add(tile(start));
        Collections.reverse(path);
        return path;
    }

    private List<WorldPoint> compressPath(List<Integer> path) {
        List<WorldPoint> points = new ArrayList<>();
        points.add(point(path.get(0)));
        if (path.size() == 1) {
            return points;
        }

        int previousHorizontal = x(path.get(1)) - x(path.get(0));
        int previousVertical = y(path.get(1)) - y(path.get(0));
        for (int index = 2; index < path.size(); index++) {
            int horizontal = x(path.get(index)) - x(path.get(index - 1));
            int vertical = y(path.get(index)) - y(path.get(index - 1));
            if (horizontal != previousHorizontal || vertical != previousVertical) {
                points.add(point(path.get(index - 1)));
                previousHorizontal = horizontal;
                previousVertical = vertical;
            }
        }
        points.add(point(path.get(path.size() - 1)));
        return points;
    }

    private boolean navigable(int x, int y, int start, int destination) {
        if (!insideBounds(x, y)) {
            return false;
        }
        int key = key(x, y);
        if (key == start || key == destination) {
            return true;
        }
        int regionId = (x >> 6) * 256 + (y >> 6);
        int localTile = (x & 63) * REGION_LENGTH + (y & 63);
        return region(regionId).get(localTile);
    }

    private BitSet region(int regionId) {
        CompletableFuture<BitSet> loaded = waterByRegion.computeIfAbsent(regionId, this::requestRegion);
        if (Thread.currentThread() == clientThreadOwner && !loadRegion(regionId, loaded)) {
            return null;
        }
        try {
            return loaded.join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("Unable to load sailing terrain region " + regionId, exception.getCause());
        }
    }

    private CompletableFuture<BitSet> requestRegion(int regionId) {
        CompletableFuture<BitSet> loaded = new CompletableFuture<>();
        clientThreadInvoker.accept(() -> loadRegion(regionId, loaded));
        return loaded;
    }

    private boolean loadRegion(int regionId, CompletableFuture<BitSet> loaded) {
        if (loaded.isDone()) {
            return true;
        }
        try {
            BitSet water = loadRegion(regionId);
            if (water == null) {
                return false;
            }
            loaded.complete(water);
        } catch (RuntimeException exception) {
            loaded.completeExceptionally(exception);
        }
        return true;
    }

    private BitSet loadRegion(int regionId) {
        if (Thread.currentThread() != clientThreadOwner) {
            throw new IllegalStateException("Sailing terrain must be loaded on the client thread");
        }
        BitSet water = new BitSet(REGION_TILE_COUNT);
        int[] files = mapIndex.getFileIds(regionId);
        if (files == null || files.length == 0) {
            return water;
        }
        byte[] terrain = mapIndex.loadData(regionId, 0);
        if (terrain == null) {
            return null;
        }

        int position = 0;
        for (int plane = 0; plane < 4; plane++) {
            for (int x = 0; x < REGION_LENGTH; x++) {
                for (int y = 0; y < REGION_LENGTH; y++) {
                    int overlay = 0;
                    while (true) {
                        int attribute = unsignedShort(terrain, position);
                        position += 2;
                        if (attribute == 0) {
                            break;
                        }
                        if (attribute == 1) {
                            position++;
                            break;
                        }
                        if (attribute <= 49) {
                            overlay = unsignedShort(terrain, position) & 0x7fff;
                            position += 2;
                        }
                    }
                    if (plane == 0 && WATER_OVERLAYS.contains(overlay)) {
                        water.set(x * REGION_LENGTH + y);
                    }
                }
            }
        }
        return water;
    }

    private static Map<Integer, List<Offset>> footprintMasks() {
        Map<Integer, List<Offset>> masks = new HashMap<>();
        for (BoatSize boatSize : BoatSize.values()) {
            for (int heading = 0; heading < HEADING_COUNT; heading++) {
                masks.put(
                        boatSize.ordinal() * HEADING_COUNT + heading,
                        offsets(boatSize, heading * Math.PI * 2 / HEADING_COUNT, 0, 0));
            }
        }
        return Collections.unmodifiableMap(masks);
    }

    private static Map<Integer, List<Offset>> turnMasks() {
        Map<Integer, List<Offset>> masks = new HashMap<>();
        for (BoatSize boatSize : BoatSize.values()) {
            for (int from = 0; from < DIRECTION_COUNT; from++) {
                for (int to = 0; to < DIRECTION_COUNT; to++) {
                    if (from == to) {
                        continue;
                    }
                    for (int turnDirection : new int[] {1, -1}) {
                        Set<Offset> offsets = new HashSet<>();
                        int angle = from * DEGREES_PER_DIRECTION;
                        int destination = to * DEGREES_PER_DIRECTION;
                        offsets.addAll(FOOTPRINTS.get(boatSize.ordinal() * HEADING_COUNT + angle));
                        while (angle != destination) {
                            angle = (angle + turnDirection + HEADING_COUNT) % HEADING_COUNT;
                            offsets.addAll(FOOTPRINTS.get(boatSize.ordinal() * HEADING_COUNT + angle));
                        }
                        masks.put(turnKey(boatSize, from, to, turnDirection), List.copyOf(offsets));
                    }
                }
            }
        }
        return Collections.unmodifiableMap(masks);
    }

    private static Map<Integer, List<Offset>> moveMasks() {
        Map<Integer, List<Offset>> masks = new HashMap<>();
        for (BoatSize boatSize : BoatSize.values()) {
            for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                masks.put(
                        boatSize.ordinal() * DIRECTION_COUNT + direction,
                        offsets(
                                boatSize,
                                direction * Math.PI * 2 / DIRECTION_COUNT,
                                HORIZONTAL[direction],
                                VERTICAL[direction]));
            }
        }
        return Collections.unmodifiableMap(masks);
    }

    private static int turnKey(BoatSize boatSize, int from, int to, int turnDirection) {
        return ((boatSize.ordinal() * DIRECTION_COUNT + from) * DIRECTION_COUNT + to) * 2 + (turnDirection > 0 ? 1 : 0);
    }

    private static List<Offset> offsets(BoatSize boatSize, double angle, int moveX, int moveY) {
        Set<Offset> offsets = new HashSet<>();
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        int radius = (int) Math.ceil(Math.hypot(boatSize.length / 2.0, boatSize.width / 2.0) + 2);
        int lastStep = moveX == 0 && moveY == 0 ? 0 : 16;
        for (int horizontal = -radius; horizontal <= radius; horizontal++) {
            for (int vertical = -radius; vertical <= radius; vertical++) {
                for (int step = 0; step <= lastStep; step++) {
                    double progress = step / 16.0;
                    if (intersects(
                            horizontal - moveX * progress, vertical - moveY * progress, cosine, sine, boatSize)) {
                        offsets.add(new Offset(horizontal, vertical));
                        break;
                    }
                }
            }
        }
        return List.copyOf(offsets);
    }

    private static boolean intersects(
            double horizontal, double vertical, double cosine, double sine, BoatSize boatSize) {
        double along = horizontal * cosine + vertical * sine;
        double across = -horizontal * sine + vertical * cosine;
        double tileHalfExtent = (Math.abs(cosine) + Math.abs(sine)) / 2;
        return Math.abs(along) < boatSize.length / 2.0 + tileHalfExtent - 0.000000001
                && Math.abs(across) < boatSize.width / 2.0 + tileHalfExtent - 0.000000001;
    }

    private static int unsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xff) << 8 | data[offset + 1] & 0xff;
    }

    private WorldPoint point(int key) {
        return new WorldPoint(x(key), y(key), 0);
    }

    private int key(int x, int y) {
        return (x - minX) * height + y - minY;
    }

    private int x(int key) {
        return key / height + minX;
    }

    private int y(int key) {
        return key % height + minY;
    }

    private static int state(int tile, int heading) {
        return tile * HEADING_STATES + heading;
    }

    private static int tile(int state) {
        return state / HEADING_STATES;
    }

    private static int heading(int state) {
        return state % HEADING_STATES;
    }

    private boolean insideBounds(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    private double heuristic(int fromX, int fromY, int toX, int toY) {
        int horizontal = Math.abs(toX - fromX);
        int vertical = Math.abs(toY - fromY);
        return heuristicWeight * (Math.max(horizontal, vertical) + (Math.sqrt(2) - 1) * Math.min(horizontal, vertical));
    }

    private static int minimumPortCoordinate(boolean horizontal) {
        int minimum = Integer.MAX_VALUE;
        for (Port port : Port.values()) {
            int coordinate = horizontal ? port.navigationLocation.getX() : port.navigationLocation.getY();
            minimum = Math.min(minimum, coordinate);
        }
        return minimum;
    }

    private static int maximumPortCoordinate(boolean horizontal) {
        int maximum = Integer.MIN_VALUE;
        for (Port port : Port.values()) {
            int coordinate = horizontal ? port.navigationLocation.getX() : port.navigationLocation.getY();
            maximum = Math.max(maximum, coordinate);
        }
        return maximum;
    }

    private static final class Offset {
        private final int horizontal;
        private final int vertical;

        private Offset(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Offset)) {
                return false;
            }
            Offset offset = (Offset) other;
            return horizontal == offset.horizontal && vertical == offset.vertical;
        }

        @Override
        public int hashCode() {
            return 31 * horizontal + vertical;
        }
    }

    private static final class Visit {
        private final int state;
        private final double estimate;
        private final double distance;

        private Visit(int state, double estimate, double distance) {
            this.state = state;
            this.estimate = estimate;
            this.distance = distance;
        }
    }

    private final class PathSearch implements SailingSearch {
        private final WorldPoint from;
        private final WorldPoint to;
        private final BoatSize boatSize;
        private final int start;
        private final int destination;
        private final int startState;
        private final int terrainRadius;
        private final Map<Integer, Double> distances = new HashMap<>();
        private final Map<Integer, Integer> previous = new HashMap<>();
        private final PriorityQueue<Visit> queue =
                new PriorityQueue<>(Comparator.comparingDouble(visit -> visit.estimate));
        private Optional<RouteLeg> result;
        private int expandedStates;

        private PathSearch(WorldPoint from, WorldPoint to, BoatSize boatSize, int start, int destination) {
            this.from = from;
            this.to = to;
            this.boatSize = boatSize;
            this.start = start;
            this.destination = destination;
            terrainRadius = (int) Math.ceil(Math.hypot(boatSize.length / 2.0, boatSize.width / 2.0) + 2);
            startState = state(start, INITIAL_HEADING);
            distances.put(startState, 0.0);
            queue.add(new Visit(startState, heuristic(from.getX(), from.getY(), to.getX(), to.getY()), 0));
        }

        @Override
        public boolean advance(int maximumExpandedStates) {
            if (result != null) {
                return true;
            }
            int initialExpandedStates = expandedStates;
            while (!queue.isEmpty() && expandedStates - initialExpandedStates < maximumExpandedStates) {
                Visit visit = queue.remove();
                if (visit.distance > distances.getOrDefault(visit.state, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                int tile = tile(visit.state);
                if (tile == destination) {
                    double distance = distances.get(visit.state);
                    List<WorldPoint> points = compressPath(path(startState, visit.state, previous));
                    result = Optional.of(new RouteLeg(null, null, distance, points));
                    return true;
                }

                int x = x(tile);
                int y = y(tile);
                if (!terrainLoaded(x, y)) {
                    queue.add(visit);
                    return false;
                }
                expandedStates++;
                int previousHeading = heading(visit.state);
                for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                    int horizontal = HORIZONTAL[direction];
                    int vertical = VERTICAL[direction];
                    int nextX = x + horizontal;
                    int nextY = y + vertical;
                    if (!insideBounds(nextX, nextY)) {
                        continue;
                    }
                    if (horizontal != 0
                            && vertical != 0
                            && (!navigable(x + horizontal, y, start, destination)
                                    || !navigable(x, y + vertical, start, destination))) {
                        continue;
                    }
                    if (!turnFits(tile, previousHeading, direction, boatSize, start, destination)
                            || !moveFits(x, y, direction, boatSize, start, destination)) {
                        continue;
                    }

                    int next = key(nextX, nextY);
                    int nextState = state(next, direction);
                    double distance = visit.distance + (horizontal != 0 && vertical != 0 ? Math.sqrt(2) : 1);
                    if (distance >= distances.getOrDefault(nextState, Double.POSITIVE_INFINITY)) {
                        continue;
                    }
                    distances.put(nextState, distance);
                    previous.put(nextState, visit.state);
                    queue.add(new Visit(nextState, distance + heuristic(nextX, nextY, to.getX(), to.getY()), distance));
                }
            }
            if (queue.isEmpty()) {
                result = Optional.empty();
            }
            return result != null;
        }

        private boolean terrainLoaded(int x, int y) {
            for (int regionX = Math.max(minX, x - terrainRadius) >> 6;
                    regionX <= Math.min(maxX, x + terrainRadius) >> 6;
                    regionX++) {
                for (int regionY = Math.max(minY, y - terrainRadius) >> 6;
                        regionY <= Math.min(maxY, y + terrainRadius) >> 6;
                        regionY++) {
                    if (region(regionX * 256 + regionY) == null) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public Optional<RouteLeg> result() {
            if (result == null) {
                throw new IllegalStateException("Sailing search is not complete");
            }
            return result;
        }

        @Override
        public int expandedStates() {
            return expandedStates;
        }
    }

    private static final class CompletedSearch implements SailingSearch {
        private final Optional<RouteLeg> result;

        private CompletedSearch(Optional<RouteLeg> result) {
            this.result = result;
        }

        @Override
        public boolean advance(int maximumExpandedStates) {
            return true;
        }

        @Override
        public Optional<RouteLeg> result() {
            return result;
        }

        @Override
        public int expandedStates() {
            return 0;
        }
    }
}
