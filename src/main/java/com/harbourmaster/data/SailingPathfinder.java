package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReferenceArray;
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
    private static final int LIVE_REFINEMENT_CHECKS = 4096;
    private static final Set<Integer> WATER_OVERLAYS =
            Set.of(442, 445, 448, 451, 454, 457, 460, 463, 466, 469, 565, 568, 571, 574, 577);
    private static final int[] HORIZONTAL = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] VERTICAL = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final Offset[][] FOOTPRINTS = footprintMasks();
    private static final Offset[][] TURNS = turnMasks();
    private static final Offset[][] MOVES = moveMasks();

    private final IndexDataBase mapIndex;
    private final SailingObstacles obstacles;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int height;
    private final double heuristicWeight;
    private WorldPoint preparedDestination;
    private double[] destinationDistances;
    private BoatSize preparedBoatSize;
    private BitSet[] preparedMoves;
    private final AtomicReferenceArray<CompletableFuture<BitSet>> waterByRegion = new AtomicReferenceArray<>(1 << 16);
    private final Consumer<BooleanSupplier> clientThreadInvoker;
    private final Thread clientThreadOwner;

    public SailingPathfinder(IndexDataBase mapIndex, Consumer<BooleanSupplier> clientThreadInvoker) {
        this(mapIndex, clientThreadInvoker, LIVE_HEURISTIC_WEIGHT, SailingObstacles.load());
    }

    public SailingPathfinder(IndexDataBase mapIndex, double heuristicWeight, SailingObstacles obstacles) {
        this(mapIndex, BooleanSupplier::getAsBoolean, heuristicWeight, obstacles);
    }

    private SailingPathfinder(
            IndexDataBase mapIndex,
            Consumer<BooleanSupplier> clientThreadInvoker,
            double heuristicWeight,
            SailingObstacles obstacles) {
        this(
                mapIndex,
                minimumPortCoordinate(true) - BOUNDS_MARGIN,
                maximumPortCoordinate(true) + BOUNDS_MARGIN,
                minimumPortCoordinate(false) - BOUNDS_MARGIN,
                maximumPortCoordinate(false) + BOUNDS_MARGIN,
                clientThreadInvoker,
                heuristicWeight,
                obstacles);
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
        this(
                mapIndex,
                minX,
                maxX,
                minY,
                maxY,
                clientThreadInvoker,
                LIVE_HEURISTIC_WEIGHT,
                new SailingObstacles(Map.of()));
    }

    private SailingPathfinder(
            IndexDataBase mapIndex,
            int minX,
            int maxX,
            int minY,
            int maxY,
            Consumer<BooleanSupplier> clientThreadInvoker,
            double heuristicWeight,
            SailingObstacles obstacles) {
        this.mapIndex = mapIndex;
        this.obstacles = obstacles;
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

    public SailingSearch search(WorldPoint from, WorldPoint to, BoatSize boatSize) {
        if (from.getPlane() != 0
                || to.getPlane() != 0
                || !insideBounds(from.getX(), from.getY())
                || !insideBounds(to.getX(), to.getY())) {
            return new CompletedSearch(Optional.empty());
        }

        int start = key(from.getX(), from.getY());
        int destination = key(to.getX(), to.getY());
        return new PathSearch(from, to, boatSize, start, destination);
    }

    public void prepareDestination(WorldPoint destination, BoatSize boatSize) {
        if (preparedBoatSize != boatSize) {
            prepareMoves(boatSize);
        }
        preparedDestination = destination;
        // Ignore turn restrictions here so these distances remain a lower bound.
        destinationDistances = new double[(maxX - minX + 1) * height];
        Arrays.fill(destinationDistances, Double.POSITIVE_INFINITY);
        int destinationKey = key(destination.getX(), destination.getY());
        destinationDistances[destinationKey] = 0;
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(visit -> visit.distance));
        queue.add(new Visit(destinationKey, 0, 0));
        while (!queue.isEmpty()) {
            Visit visit = queue.remove();
            if (visit.distance > destinationDistances[visit.state]) {
                continue;
            }
            int x = x(visit.state);
            int y = y(visit.state);
            for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                int horizontal = HORIZONTAL[direction];
                int vertical = VERTICAL[direction];
                int nextX = x + horizontal;
                int nextY = y + vertical;
                if (!preparedMoves[direction].get(visit.state)) {
                    continue;
                }
                int next = key(nextX, nextY);
                double distance = visit.distance + (horizontal != 0 && vertical != 0 ? Math.sqrt(2) : 1);
                if (distance >= destinationDistances[next]) {
                    continue;
                }
                destinationDistances[next] = distance;
                queue.add(new Visit(next, distance, distance));
            }
        }
    }

    private void prepareMoves(BoatSize boatSize) {
        int tileCount = (maxX - minX + 1) * height;
        BitSet water = new BitSet(tileCount);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (navigable(x, y)) {
                    water.set(key(x, y));
                }
            }
        }
        long[] terrain = Arrays.copyOf(water.toLongArray(), (tileCount + 63) / 64);
        preparedMoves = new BitSet[DIRECTION_COUNT];
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            long[] allowed = new long[terrain.length];
            Arrays.fill(allowed, -1L);
            int minimumHorizontal = 0;
            int maximumHorizontal = 0;
            int minimumVertical = 0;
            int maximumVertical = 0;
            // Intersect the water map shifted by each tile covered during a move.
            for (Offset offset : MOVES[boatSize.ordinal() * DIRECTION_COUNT + direction]) {
                int shift = offset.horizontal * height + offset.vertical;
                int words = Math.floorDiv(shift, 64);
                int bits = Math.floorMod(shift, 64);
                for (int index = 0; index < allowed.length; index++) {
                    int source = index + words;
                    long tiles = source >= 0 && source < terrain.length ? terrain[source] >>> bits : 0;
                    if (bits != 0 && source + 1 >= 0 && source + 1 < terrain.length) {
                        tiles |= terrain[source + 1] << (64 - bits);
                    }
                    allowed[index] &= tiles;
                }
                minimumHorizontal = Math.min(minimumHorizontal, offset.horizontal);
                maximumHorizontal = Math.max(maximumHorizontal, offset.horizontal);
                minimumVertical = Math.min(minimumVertical, offset.vertical);
                maximumVertical = Math.max(maximumVertical, offset.vertical);
            }
            BitSet moves = BitSet.valueOf(allowed);
            moves.clear(tileCount, terrain.length * 64);
            moves.clear(0, -minimumHorizontal * height);
            moves.clear(tileCount - maximumHorizontal * height, tileCount);
            for (int x = minX; x <= maxX; x++) {
                int column = key(x, minY);
                moves.clear(column, column - minimumVertical);
                moves.clear(column + height - maximumVertical, column + height);
            }
            preparedMoves[direction] = moves;
        }
        preparedBoatSize = boatSize;
    }

    public static boolean includesRegion(int regionId) {
        int x = regionId >> 8;
        int y = regionId & 255;
        return x >= (minimumPortCoordinate(true) - BOUNDS_MARGIN) >> 6
                && x <= (maximumPortCoordinate(true) + BOUNDS_MARGIN) >> 6
                && y >= (minimumPortCoordinate(false) - BOUNDS_MARGIN) >> 6
                && y <= (maximumPortCoordinate(false) + BOUNDS_MARGIN) >> 6;
    }

    public static int prepareMasks() {
        return FOOTPRINTS.length + TURNS.length + MOVES.length;
    }

    private boolean turnFits(int tile, int previousHeading, int nextHeading, BoatSize boatSize) {
        if (previousHeading == INITIAL_HEADING || previousHeading == nextHeading) {
            return true;
        }

        int clockwiseSteps = (nextHeading - previousHeading + DIRECTION_COUNT) % DIRECTION_COUNT;
        if (clockwiseSteps == DIRECTION_COUNT / 2) {
            return turnFits(tile, previousHeading, nextHeading, boatSize, 1)
                    || turnFits(tile, previousHeading, nextHeading, boatSize, -1);
        }
        return turnFits(tile, previousHeading, nextHeading, boatSize, clockwiseSteps < DIRECTION_COUNT / 2 ? 1 : -1);
    }

    private boolean turnFits(int tile, int previousHeading, int nextHeading, BoatSize boatSize, int turnDirection) {
        int x = x(tile);
        int y = y(tile);
        for (Offset offset : TURNS[turnKey(boatSize, previousHeading, nextHeading, turnDirection)]) {
            if (!navigable(x + offset.horizontal, y + offset.vertical)) {
                return false;
            }
        }
        return true;
    }

    private boolean moveFits(int x, int y, int direction, BoatSize boatSize) {
        if (preparedBoatSize == boatSize) {
            return preparedMoves[direction].get(key(x, y));
        }
        for (Offset offset : MOVES[boatSize.ordinal() * DIRECTION_COUNT + direction]) {
            if (!navigable(x + offset.horizontal, y + offset.vertical)) {
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

    private List<WorldPoint> straighten(List<WorldPoint> points, BoatSize boatSize) {
        List<WorldPoint> result = new ArrayList<>();
        int anchor = 0;
        result.add(points.get(anchor));
        while (anchor < points.size() - 1) {
            int next = anchor + 1;
            for (int candidate = points.size() - 1; candidate > next; candidate--) {
                WorldPoint from = points.get(anchor);
                WorldPoint to = points.get(candidate);
                double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
                if (!segmentFits(from, to, boatSize)) {
                    continue;
                }
                if (result.size() > 1) {
                    WorldPoint previous = result.get(result.size() - 2);
                    if (!turnFits(
                            from,
                            Math.atan2(from.getY() - previous.getY(), from.getX() - previous.getX()),
                            angle,
                            boatSize)) {
                        continue;
                    }
                }
                if (candidate < points.size() - 1) {
                    WorldPoint following = points.get(candidate + 1);
                    if (!turnFits(
                            to,
                            angle,
                            Math.atan2(following.getY() - to.getY(), following.getX() - to.getX()),
                            boatSize)) {
                        continue;
                    }
                }
                next = candidate;
                break;
            }
            result.add(points.get(next));
            anchor = next;
        }
        return result;
    }

    private boolean segmentFits(WorldPoint from, WorldPoint to, BoatSize boatSize) {
        return segmentFits(from, to, boatSize, new CollisionBudget(Long.MAX_VALUE));
    }

    private boolean segmentFits(WorldPoint from, WorldPoint to, BoatSize boatSize, CollisionBudget budget) {
        int horizontal = to.getX() - from.getX();
        int vertical = to.getY() - from.getY();
        double distance = Math.hypot(horizontal, vertical);
        if (distance == 0) {
            return false;
        }
        double cosine = horizontal / distance;
        double sine = vertical / distance;
        double centerX = (from.getX() + to.getX()) / 2.0;
        double centerY = (from.getY() + to.getY()) / 2.0;
        int radius = (int) Math.ceil(Math.hypot(boatSize.length, boatSize.width) / 2 + 1);
        boolean horizontalMajor = Math.abs(horizontal) >= Math.abs(vertical);
        int start = horizontalMajor ? from.getX() : from.getY();
        int end = horizontalMajor ? to.getX() : to.getY();
        for (int major = Math.min(start, end) - radius; major <= Math.max(start, end) + radius; major++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Sailing search cancelled");
            }
            double progress = Math.max(0, Math.min(1, (major - start) / (double) (end - start)));
            double minorCenter =
                    horizontalMajor ? from.getY() + vertical * progress : from.getX() + horizontal * progress;
            for (int minor = (int) Math.floor(minorCenter) - 2 * radius;
                    minor <= Math.ceil(minorCenter) + 2 * radius;
                    minor++) {
                int x = horizontalMajor ? major : minor;
                int y = horizontalMajor ? minor : major;
                if (rectangleIntersects(
                                x - centerX,
                                y - centerY,
                                cosine,
                                sine,
                                (boatSize.length + distance) / 2,
                                boatSize.width / 2.0)
                        && !budget.navigable(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean turnFits(WorldPoint point, double from, double to, BoatSize boatSize) {
        return turnFits(point, from, to, boatSize, new CollisionBudget(Long.MAX_VALUE));
    }

    private boolean turnFits(WorldPoint point, double from, double to, BoatSize boatSize, CollisionBudget budget) {
        double rotation = Math.atan2(Math.sin(to - from), Math.cos(to - from));
        if (Math.abs(rotation) < 0.000000001) {
            return true;
        }
        int steps = (int) Math.ceil(Math.abs(rotation) * 180 / Math.PI);
        double radius = Math.hypot(boatSize.length, boatSize.width) / 2;
        // Inflate each sampled hull to cover its motion between adjacent angles.
        double margin = radius * Math.abs(rotation) / (2 * steps);
        int extent = (int) Math.ceil(radius + margin + 1);
        BitSet checked = new BitSet((2 * extent + 1) * (2 * extent + 1));
        for (int step = 0; step <= steps; step++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Sailing search cancelled");
            }
            double angle = from + rotation * step / steps;
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            for (int x = -extent; x <= extent; x++) {
                for (int y = -extent; y <= extent; y++) {
                    int tile = (x + extent) * (2 * extent + 1) + y + extent;
                    if (checked.get(tile)
                            || !rectangleIntersects(
                                    x,
                                    y,
                                    cosine,
                                    sine,
                                    boatSize.length / 2.0 + margin,
                                    boatSize.width / 2.0 + margin)) {
                        continue;
                    }
                    if (!budget.navigable(point.getX() + x, point.getY() + y)) {
                        return false;
                    }
                    checked.set(tile);
                }
            }
        }
        return true;
    }

    private static boolean rectangleIntersects(
            double x, double y, double cosine, double sine, double halfLength, double halfWidth) {
        double tileExtent = (Math.abs(cosine) + Math.abs(sine)) / 2;
        return Math.abs(x * cosine + y * sine) < halfLength + tileExtent - 0.000000001
                && Math.abs(-x * sine + y * cosine) < halfWidth + tileExtent - 0.000000001
                && Math.abs(x) < halfLength * Math.abs(cosine) + halfWidth * Math.abs(sine) + 0.5 - 0.000000001
                && Math.abs(y) < halfLength * Math.abs(sine) + halfWidth * Math.abs(cosine) + 0.5 - 0.000000001;
    }

    private RouteLeg straightenedRoute(List<WorldPoint> points, BoatSize boatSize) {
        return routeLeg(null, null, straighten(points, boatSize));
    }

    public RouteLeg refineRoute(RouteLeg route, BoatSize boatSize) {
        return new RouteRefinement(route, boatSize, Long.MAX_VALUE).refine();
    }

    private static RouteLeg routeLeg(Port from, Port to, List<WorldPoint> points) {
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            distance += distance(points.get(index - 1), points.get(index));
        }
        return new RouteLeg(from, to, distance, points);
    }

    private static double distance(WorldPoint from, WorldPoint to) {
        return Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
    }

    private static double angle(WorldPoint from, WorldPoint to) {
        return Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
    }

    private final class CollisionBudget {
        private long remaining;
        private final boolean loadedOnly;

        private CollisionBudget(long maximumChecks) {
            remaining = maximumChecks;
            loadedOnly = maximumChecks != Long.MAX_VALUE;
        }

        private boolean navigable(int x, int y) {
            if (remaining == 0) {
                return false;
            }
            remaining--;
            if (!loadedOnly) {
                return SailingPathfinder.this.navigable(x, y);
            }
            if (!insideBounds(x, y)) {
                return false;
            }
            CompletableFuture<BitSet> loaded = waterByRegion.get((x >> 6) * 256 + (y >> 6));
            return loaded != null && loaded.isDone() && loaded.join().get((x & 63) * REGION_LENGTH + (y & 63));
        }
    }

    private final class RouteRefinement {
        private final RouteLeg original;
        private final List<WorldPoint> points;
        private final BoatSize boatSize;
        private final CollisionBudget budget;

        private RouteRefinement(RouteLeg route, BoatSize boatSize, long maximumChecks) {
            original = route;
            points = new ArrayList<>(route.points);
            this.boatSize = boatSize;
            budget = new CollisionBudget(maximumChecks);
        }

        private RouteLeg refine() {
            boolean changed;
            do {
                changed = false;
                int maximumStep = 1;
                for (int index = 1; index < points.size(); index++) {
                    maximumStep =
                            Math.max(maximumStep, (int) Math.ceil(distance(points.get(index - 1), points.get(index))));
                }
                if (budget.loadedOnly) {
                    maximumStep = Math.min(maximumStep, boatSize.length);
                }
                for (int step = Integer.highestOneBit(maximumStep); step > 0 && budget.remaining > 0; step /= 2) {
                    boolean improved;
                    do {
                        improved = false;
                        if (budget.loadedOnly) {
                            improved = improveLiveCorners(step);
                            changed |= improved;
                            continue;
                        }
                        for (int index = 1; index < points.size() - 1 && budget.remaining > 0; index++) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new CancellationException("Sailing search cancelled");
                            }
                            if (improveCorner(index, step)) {
                                improved = true;
                                changed = true;
                            }
                        }
                    } while (improved && budget.remaining > 0);
                }
            } while (changed && budget.remaining > 0);
            return routeLeg(original.from, original.to, points);
        }

        private boolean improveLiveCorners(int step) {
            List<Integer> corners = new ArrayList<>();
            for (int index = 1; index < points.size() - 1; index++) {
                corners.add(index);
            }
            corners.sort(
                    Comparator.comparingDouble((Integer index) -> distance(points.get(index - 1), points.get(index))
                                    + distance(points.get(index), points.get(index + 1))
                                    - distance(points.get(index - 1), points.get(index + 1)))
                            .reversed());
            for (int index : corners) {
                if (budget.remaining == 0) {
                    break;
                }
                if (improveCorner(index, step)) {
                    return true;
                }
            }
            return false;
        }

        private boolean improveCorner(int index, int step) {
            if (replaceCorner(index, List.of())) {
                return true;
            }
            WorldPoint previous = points.get(index - 1);
            WorldPoint corner = points.get(index);
            WorldPoint next = points.get(index + 1);
            // New points along both legs let the turn begin before the old corner.
            if (replaceCorner(index, List.of(towards(corner, previous, step), towards(corner, next, step)))) {
                return true;
            }
            List<WorldPoint> candidates = new ArrayList<>();
            for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                candidates.add(new WorldPoint(
                        corner.getX() + HORIZONTAL[direction] * step, corner.getY() + VERTICAL[direction] * step, 0));
            }
            candidates.sort(Comparator.comparingDouble(point -> distance(previous, point) + distance(point, next)));
            for (WorldPoint candidate : candidates) {
                if (budget.remaining == 0) {
                    break;
                }
                if (replaceCorner(index, List.of(candidate))) {
                    return true;
                }
            }
            return false;
        }

        private WorldPoint towards(WorldPoint from, WorldPoint to, int step) {
            double progress = Math.min(1, step / distance(from, to));
            return new WorldPoint(
                    (int) Math.round(from.getX() + (to.getX() - from.getX()) * progress),
                    (int) Math.round(from.getY() + (to.getY() - from.getY()) * progress),
                    0);
        }

        private boolean replaceCorner(int index, List<WorldPoint> replacement) {
            List<WorldPoint> candidate = new ArrayList<>();
            candidate.add(points.get(index - 1));
            for (WorldPoint point : replacement) {
                if (!point.equals(candidate.get(candidate.size() - 1))) {
                    candidate.add(point);
                }
            }
            WorldPoint next = points.get(index + 1);
            if (!next.equals(candidate.get(candidate.size() - 1))) {
                candidate.add(next);
            }
            if (candidate.size() < 2) {
                return false;
            }
            double distance = 0;
            for (int position = 1; position < candidate.size(); position++) {
                distance += distance(candidate.get(position - 1), candidate.get(position));
            }
            if (distance
                    >= distance(points.get(index - 1), points.get(index))
                            + distance(points.get(index), next)
                            - 0.0000001) {
                return false;
            }
            for (int position = 1; position < candidate.size(); position++) {
                if (!segmentFits(candidate.get(position - 1), candidate.get(position), boatSize, budget)) {
                    return false;
                }
            }
            for (int position = 0; position < candidate.size(); position++) {
                WorldPoint before =
                        position > 0 ? candidate.get(position - 1) : index > 1 ? points.get(index - 2) : null;
                WorldPoint after = position < candidate.size() - 1
                        ? candidate.get(position + 1)
                        : index + 2 < points.size() ? points.get(index + 2) : null;
                WorldPoint corner = candidate.get(position);
                if (before != null
                        && after != null
                        && !turnFits(corner, angle(before, corner), angle(corner, after), boatSize, budget)) {
                    return false;
                }
            }
            points.remove(index);
            points.addAll(index, candidate.subList(1, candidate.size() - 1));
            return true;
        }
    }

    private boolean navigable(int x, int y) {
        if (!insideBounds(x, y)) {
            return false;
        }
        int regionId = (x >> 6) * 256 + (y >> 6);
        int localTile = (x & 63) * REGION_LENGTH + (y & 63);
        BitSet water = region(regionId);
        return water != null && water.get(localTile);
    }

    private BitSet region(int regionId) {
        CompletableFuture<BitSet> loaded = waterByRegion.get(regionId);
        if (loaded == null) {
            CompletableFuture<BitSet> requested = new CompletableFuture<>();
            if (waterByRegion.compareAndSet(regionId, null, requested)) {
                clientThreadInvoker.accept(() -> loadRegion(regionId, requested));
            }
            loaded = waterByRegion.get(regionId);
        }
        if (Thread.currentThread() == clientThreadOwner && !loadRegion(regionId, loaded)) {
            return null;
        }
        try {
            return loaded.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Sailing search cancelled");
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to load sailing terrain region " + regionId, exception.getCause());
        }
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
                    int shape = 0;
                    int settings = 0;
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
                            shape = (attribute - 2) / 4;
                        } else if (attribute <= 81) {
                            settings = attribute - 49;
                        }
                    }
                    if (plane == 0 && WATER_OVERLAYS.contains(overlay) && shape == 0 && (settings & 1) == 0) {
                        water.set(x * REGION_LENGTH + y);
                    }
                    if (plane == 1 && (settings & 2) != 0) {
                        water.clear(x * REGION_LENGTH + y);
                    }
                }
            }
        }
        obstacles.removeFrom(regionId, water);
        return water;
    }

    private static Offset[][] footprintMasks() {
        Offset[][] masks = new Offset[BoatSize.values().length * HEADING_COUNT][];
        for (BoatSize boatSize : BoatSize.values()) {
            for (int heading = 0; heading < HEADING_COUNT; heading++) {
                masks[boatSize.ordinal() * HEADING_COUNT + heading] =
                        offsets(boatSize, heading * Math.PI * 2 / HEADING_COUNT, 0, 0);
            }
        }
        return masks;
    }

    private static Offset[][] turnMasks() {
        Offset[][] masks = new Offset[BoatSize.values().length * DIRECTION_COUNT * DIRECTION_COUNT * 2][];
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
                        Collections.addAll(offsets, FOOTPRINTS[boatSize.ordinal() * HEADING_COUNT + angle]);
                        while (angle != destination) {
                            angle = (angle + turnDirection + HEADING_COUNT) % HEADING_COUNT;
                            Collections.addAll(offsets, FOOTPRINTS[boatSize.ordinal() * HEADING_COUNT + angle]);
                        }
                        masks[turnKey(boatSize, from, to, turnDirection)] = offsets.toArray(new Offset[0]);
                    }
                }
            }
        }
        return masks;
    }

    private static Offset[][] moveMasks() {
        Offset[][] masks = new Offset[BoatSize.values().length * DIRECTION_COUNT][];
        for (BoatSize boatSize : BoatSize.values()) {
            for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                masks[boatSize.ordinal() * DIRECTION_COUNT + direction] = offsets(
                        boatSize,
                        direction * Math.PI * 2 / DIRECTION_COUNT,
                        HORIZONTAL[direction],
                        VERTICAL[direction]);
            }
        }
        return masks;
    }

    private static int turnKey(BoatSize boatSize, int from, int to, int turnDirection) {
        return ((boatSize.ordinal() * DIRECTION_COUNT + from) * DIRECTION_COUNT + to) * 2 + (turnDirection > 0 ? 1 : 0);
    }

    private static Offset[] offsets(BoatSize boatSize, double angle, int moveX, int moveY) {
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
        return offsets.toArray(new Offset[0]);
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

    private double heuristic(int fromX, int fromY, int toX, int toY, BoatSize boatSize) {
        if (preparedBoatSize == boatSize
                && preparedDestination != null
                && preparedDestination.getX() == toX
                && preparedDestination.getY() == toY) {
            return heuristicWeight * destinationDistances[key(fromX, fromY)];
        }
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
        private final int destination;
        private final int startState;
        private final int terrainRadius;
        private final Map<Integer, Double> distances = new HashMap<>();
        private final Map<Integer, Integer> previous = new HashMap<>();
        private final PriorityQueue<Visit> queue =
                new PriorityQueue<>(Comparator.comparingDouble(visit -> visit.estimate));
        private Optional<RouteLeg> result;
        private int expandedStates;
        private boolean endpointsChecked;

        private PathSearch(WorldPoint from, WorldPoint to, BoatSize boatSize, int start, int destination) {
            this.from = from;
            this.to = to;
            this.boatSize = boatSize;
            this.destination = destination;
            terrainRadius = (int) Math.ceil(Math.hypot(boatSize.length / 2.0, boatSize.width / 2.0) + 2);
            startState = state(start, INITIAL_HEADING);
            distances.put(startState, 0.0);
            queue.add(new Visit(startState, heuristic(from.getX(), from.getY(), to.getX(), to.getY(), boatSize), 0));
        }

        @Override
        public boolean advance(int maximumExpandedStates) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Sailing search cancelled");
            }
            if (result != null) {
                return true;
            }
            if (!endpointsChecked) {
                if (!terrainLoaded(from.getX(), from.getY()) || !terrainLoaded(to.getX(), to.getY())) {
                    return false;
                }
                if (!footprintFits(from)
                        || !footprintFits(to)
                        || !Double.isFinite(heuristic(from.getX(), from.getY(), to.getX(), to.getY(), boatSize))) {
                    result = Optional.empty();
                    return true;
                }
                endpointsChecked = true;
                if (segmentFits(from, to, boatSize)) {
                    result = Optional.of(new RouteLeg(
                            null,
                            null,
                            Math.hypot(to.getX() - from.getX(), to.getY() - from.getY()),
                            List.of(from, to)));
                    return true;
                }
            }
            int initialExpandedStates = expandedStates;
            while (!queue.isEmpty() && expandedStates - initialExpandedStates < maximumExpandedStates) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Sailing search cancelled");
                }
                Visit visit = queue.remove();
                if (visit.distance > distances.getOrDefault(visit.state, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                int tile = tile(visit.state);
                if (tile == destination) {
                    List<WorldPoint> points = compressPath(path(startState, visit.state, previous));
                    result = Optional.of(
                            new RouteRefinement(straightenedRoute(points, boatSize), boatSize, LIVE_REFINEMENT_CHECKS)
                                    .refine());
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
                            && (!navigable(x + horizontal, y) || !navigable(x, y + vertical))) {
                        continue;
                    }
                    if (!turnFits(tile, previousHeading, direction, boatSize) || !moveFits(x, y, direction, boatSize)) {
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
                    queue.add(new Visit(
                            nextState, distance + heuristic(nextX, nextY, to.getX(), to.getY(), boatSize), distance));
                }
            }
            if (queue.isEmpty()) {
                result = Optional.empty();
            }
            return result != null;
        }

        private boolean footprintFits(WorldPoint point) {
            for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                boolean fits = true;
                for (Offset offset :
                        FOOTPRINTS[boatSize.ordinal() * HEADING_COUNT + direction * DEGREES_PER_DIRECTION]) {
                    if (!navigable(point.getX() + offset.horizontal, point.getY() + offset.vertical)) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    return true;
                }
            }
            return false;
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
