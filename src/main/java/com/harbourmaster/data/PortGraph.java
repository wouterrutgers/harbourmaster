package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import net.runelite.api.coords.WorldPoint;

public final class PortGraph {
    private final Map<Port, List<RouteLeg>> edges = new EnumMap<>(Port.class);
    private final Map<Integer, Optional<RouteLeg>> cache = new HashMap<>();

    private WorldPoint boatPosition;
    private final List<RouteLeg> paths;
    private SailingGraph sailing;
    private final Map<Port, Optional<RouteLeg>> boatRoutes = new EnumMap<>(Port.class);

    public PortGraph(List<RouteLeg> paths) {
        this.paths = List.copyOf(paths);
        for (RouteLeg path : paths) {
            edges.computeIfAbsent(path.from, ignored -> new ArrayList<>()).add(path);
            List<WorldPoint> reverse = new ArrayList<>(path.points);
            Collections.reverse(reverse);
            edges.computeIfAbsent(path.to, ignored -> new ArrayList<>())
                    .add(new RouteLeg(path.to, path.from, path.distance, reverse));
        }
    }

    public Optional<RouteLeg> routeFromPosition(WorldPoint position, Port destination) {
        if (sailing == null) {
            sailing = new SailingGraph(paths);
        }
        if (!position.equals(boatPosition)) {
            boatPosition = position;
            boatRoutes.clear();
        }
        return boatRoutes.computeIfAbsent(destination, port -> sailing.route(position, port));
    }

    public Optional<RouteLeg> route(Port from, Port to) {
        if (from == null || to == null) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(
                from.ordinal() * Port.values().length + to.ordinal(), ignored -> shortest(from, to));
    }

    private Optional<RouteLeg> shortest(Port from, Port to) {
        if (from == to) {
            return Optional.of(new RouteLeg(from, to, 0, List.of(from.navigationLocation)));
        }
        Map<Port, Double> distances = new EnumMap<>(Port.class);
        Map<Port, RouteLeg> previous = new EnumMap<>(Port.class);
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(visit -> visit.distance));
        distances.put(from, 0.0);
        queue.add(new Visit(from, 0));
        while (!queue.isEmpty()) {
            Visit visit = queue.remove();
            if (visit.distance > distances.get(visit.port)) {
                continue;
            }
            if (visit.port == to) {
                break;
            }
            for (RouteLeg edge : edges.getOrDefault(visit.port, List.of())) {
                double distance = visit.distance + edge.distance;
                if (distance < distances.getOrDefault(edge.to, Double.POSITIVE_INFINITY)) {
                    distances.put(edge.to, distance);
                    previous.put(edge.to, edge);
                    queue.add(new Visit(edge.to, distance));
                }
            }
        }
        if (!distances.containsKey(to)) {
            return Optional.empty();
        }
        List<RouteLeg> segments = new ArrayList<>();
        for (Port cursor = to; cursor != from; cursor = previous.get(cursor).from) {
            segments.add(previous.get(cursor));
        }
        Collections.reverse(segments);
        List<WorldPoint> points = new ArrayList<>();
        for (RouteLeg segment : segments) {
            points.addAll(segment.points.subList(points.isEmpty() ? 0 : 1, segment.points.size()));
        }
        return Optional.of(new RouteLeg(from, to, distances.get(to), points));
    }

    private static final class Visit {
        private final Port port;
        private final double distance;

        private Visit(Port port, double distance) {
            this.port = port;
            this.distance = distance;
        }
    }
}
