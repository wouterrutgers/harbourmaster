package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

final class SailingGraph {
    private static final int PATH_JOIN_TOLERANCE = 16;
    private final Map<WorldPoint, Set<WorldPoint>> neighbors = new HashMap<>();
    private final Map<Port, WorldPoint> ports = new EnumMap<>(Port.class);
    private final Map<Port, Search> searches = new EnumMap<>(Port.class);
    private final List<Segment> segments = new ArrayList<>();

    SailingGraph(List<RouteLeg> paths) {
        List<Segment> original = new ArrayList<>();
        for (RouteLeg path : paths) {
            ports.put(path.from, path.points.get(0));
            ports.put(path.to, path.points.get(path.points.size() - 1));
            for (int index = 1; index < path.points.size(); index++) {
                WorldPoint from = path.points.get(index - 1);
                WorldPoint to = path.points.get(index);
                if (!from.equals(to)) {
                    original.add(new Segment(from, to));
                }
            }
        }
        for (int index = 0; index < original.size(); index++) {
            for (int other = index + 1; other < original.size(); other++) {
                original.get(index).intersect(original.get(other));
            }
        }
        for (Segment segment : original) {
            List<WorldPoint> points = new ArrayList<>(segment.junctions);
            points.sort(Comparator.comparingDouble(point -> length(segment.from, point)));
            for (int index = 1; index < points.size(); index++) {
                WorldPoint from = points.get(index - 1);
                WorldPoint to = points.get(index);
                if (neighbors.computeIfAbsent(from, ignored -> new HashSet<>()).add(to)) {
                    neighbors.computeIfAbsent(to, ignored -> new HashSet<>()).add(from);
                    segments.add(new Segment(from, to));
                }
            }
        }
    }

    Optional<RouteLeg> route(WorldPoint position, Port destination) {
        if (!ports.containsKey(destination)) {
            return Optional.empty();
        }
        Search search = searches.computeIfAbsent(destination, port -> new Search(ports.get(port)));
        double nearest = Double.POSITIVE_INFINITY;
        for (Segment segment : segments) {
            if (segment.from.getPlane() == position.getPlane()) {
                nearest = Math.min(nearest, length(position, segment.project(position)));
            }
        }
        double best = Double.POSITIVE_INFINITY;
        WorldPoint approach = null;
        WorldPoint entry = null;
        for (Segment segment : segments) {
            if (segment.from.getPlane() != position.getPlane()) {
                continue;
            }
            WorldPoint projection = segment.project(position);
            double connector = length(position, projection);
            if (connector > nearest + PATH_JOIN_TOLERANCE) {
                continue;
            }
            for (WorldPoint endpoint : List.of(segment.from, segment.to)) {
                double allowance = nearest + PATH_JOIN_TOLERANCE;
                double forward = Math.sqrt(Math.max(0, allowance * allowance - connector * connector));
                double remaining = length(projection, endpoint);
                double fraction = remaining == 0 ? 0 : Math.min(1, forward / remaining);
                WorldPoint merge = new WorldPoint(
                        (int) Math.round(projection.getX() + fraction * (endpoint.getX() - projection.getX())),
                        (int) Math.round(projection.getY() + fraction * (endpoint.getY() - projection.getY())),
                        position.getPlane());
                double cost = length(position, merge)
                        + length(merge, endpoint)
                        + search.distances.getOrDefault(endpoint, Double.POSITIVE_INFINITY);
                if (cost < best) {
                    best = cost;
                    approach = merge;
                    entry = endpoint;
                }
            }
        }
        if (entry == null) {
            return Optional.empty();
        }
        List<WorldPoint> points = new ArrayList<>();
        points.add(position);
        if (!position.equals(approach)) {
            points.add(approach);
        }
        for (WorldPoint point = entry; point != null; point = search.next.get(point)) {
            if (!points.get(points.size() - 1).equals(point)) {
                points.add(point);
            }
        }
        List<WorldPoint> simplified = new ArrayList<>();
        simplified.add(points.get(0));
        simplify(points, 0, points.size() - 1, simplified);
        points = simplified;
        best = 0;
        for (int index = 1; index < points.size(); index++) {
            best += length(points.get(index - 1), points.get(index));
        }
        return Optional.of(new RouteLeg(null, destination, best, points));
    }

    private static void simplify(List<WorldPoint> points, int first, int last, List<WorldPoint> result) {
        if (first == last) {
            return;
        }
        WorldPoint from = points.get(first);
        WorldPoint to = points.get(last);
        double horizontal = to.getX() - from.getX();
        double vertical = to.getY() - from.getY();
        double squared = horizontal * horizontal + vertical * vertical;
        double maximum = 0.75;
        int split = -1;
        for (int index = first + 1; index < last; index++) {
            WorldPoint point = points.get(index);
            double fraction = squared == 0
                    ? 0
                    : Math.max(
                            0,
                            Math.min(
                                    1,
                                    ((point.getX() - from.getX()) * horizontal
                                                    + (point.getY() - from.getY()) * vertical)
                                            / squared));
            double distance = Math.hypot(
                    point.getX() - from.getX() - fraction * horizontal,
                    point.getY() - from.getY() - fraction * vertical);
            if (distance > maximum) {
                maximum = distance;
                split = index;
            }
        }
        if (split < 0) {
            result.add(to);
        } else {
            simplify(points, first, split, result);
            simplify(points, split, last, result);
        }
    }

    private final class Search {
        private final Map<WorldPoint, Double> distances = new HashMap<>();
        private final Map<WorldPoint, WorldPoint> next = new HashMap<>();

        private Search(WorldPoint destination) {
            PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(visit -> visit.distance));
            distances.put(destination, 0.0);
            queue.add(new Visit(destination, 0));
            while (!queue.isEmpty()) {
                Visit visit = queue.remove();
                if (visit.distance > distances.get(visit.point)) {
                    continue;
                }
                for (WorldPoint neighbor : neighbors.getOrDefault(visit.point, Set.of())) {
                    double distance = visit.distance + length(visit.point, neighbor);
                    if (distance < distances.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                        distances.put(neighbor, distance);
                        next.put(neighbor, visit.point);
                        queue.add(new Visit(neighbor, distance));
                    }
                }
            }
        }
    }

    private static final class Visit {
        private final WorldPoint point;
        private final double distance;

        private Visit(WorldPoint point, double distance) {
            this.point = point;
            this.distance = distance;
        }
    }

    private static double length(WorldPoint from, WorldPoint to) {
        return Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
    }

    private static final class Segment {
        private final WorldPoint from;
        private final WorldPoint to;
        private final Set<WorldPoint> junctions = new HashSet<>();

        private Segment(WorldPoint from, WorldPoint to) {
            this.from = from;
            this.to = to;
            junctions.add(from);
            junctions.add(to);
        }

        private WorldPoint project(WorldPoint point) {
            double horizontal = to.getX() - from.getX();
            double vertical = to.getY() - from.getY();
            double fraction = Math.max(
                    0,
                    Math.min(
                            1,
                            ((point.getX() - from.getX()) * horizontal + (point.getY() - from.getY()) * vertical)
                                    / (horizontal * horizontal + vertical * vertical)));
            return at(fraction);
        }

        private WorldPoint at(double fraction) {
            return new WorldPoint(
                    (int) Math.round(from.getX() + fraction * (to.getX() - from.getX())),
                    (int) Math.round(from.getY() + fraction * (to.getY() - from.getY())),
                    from.getPlane());
        }

        private void intersect(Segment other) {
            if (from.getPlane() != other.from.getPlane()) {
                return;
            }
            double horizontal = to.getX() - from.getX();
            double vertical = to.getY() - from.getY();
            double otherHorizontal = other.to.getX() - other.from.getX();
            double otherVertical = other.to.getY() - other.from.getY();
            double offsetHorizontal = other.from.getX() - from.getX();
            double offsetVertical = other.from.getY() - from.getY();
            double determinant = horizontal * otherVertical - vertical * otherHorizontal;
            if (determinant == 0) {
                if (offsetHorizontal * vertical != offsetVertical * horizontal) {
                    return;
                }
                for (WorldPoint point : List.of(from, to, other.from, other.to)) {
                    if (contains(point) && other.contains(point)) {
                        junctions.add(point);
                        other.junctions.add(point);
                    }
                }
                return;
            }
            double fraction = (offsetHorizontal * otherVertical - offsetVertical * otherHorizontal) / determinant;
            double otherFraction = (offsetHorizontal * vertical - offsetVertical * horizontal) / determinant;
            if (fraction >= 0 && fraction <= 1 && otherFraction >= 0 && otherFraction <= 1) {
                WorldPoint point = at(fraction);
                junctions.add(point);
                other.junctions.add(point);
            }
        }

        private boolean contains(WorldPoint point) {
            return point.getX() >= Math.min(from.getX(), to.getX())
                    && point.getX() <= Math.max(from.getX(), to.getX())
                    && point.getY() >= Math.min(from.getY(), to.getY())
                    && point.getY() <= Math.max(from.getY(), to.getY());
        }
    }
}
