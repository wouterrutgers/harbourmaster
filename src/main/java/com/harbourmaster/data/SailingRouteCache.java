package com.harbourmaster.data;

import com.google.gson.Gson;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public final class SailingRouteCache {
    private static final int FORMAT_VERSION = 2;
    private static final Gson GSON = new Gson();

    private SailingRouteCache() {}

    public static void load(PortGraph graph, BoatSize boatSize) {
        String resource = "/com/harbourmaster/routes/" + boatSize.name().toLowerCase(Locale.ROOT) + ".json";
        InputStream source = SailingRouteCache.class.getResourceAsStream(resource);
        if (source == null) {
            throw new IllegalStateException("Missing generated sailing routes: " + resource);
        }
        try (InputStream input = source;
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            RouteFile file = GSON.fromJson(reader, RouteFile.class);
            graph.loadRoutes(boatSize, routes(file, boatSize, resource), portalRoutes(file, boatSize, resource));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read generated sailing routes: " + resource, exception);
        }
    }

    private static Map<Integer, Optional<RouteLeg>> routes(RouteFile file, BoatSize boatSize, String resource) {
        if (file.formatVersion != FORMAT_VERSION || !boatSize.name().equals(file.boatSize)) {
            throw new IllegalStateException("Unsupported sailing route data: " + resource);
        }
        int portCount = Port.values().length;
        int expectedRoutes = portCount * (portCount - 1) / 2;
        if (file.routes.size() != expectedRoutes) {
            throw new IllegalStateException("Incomplete sailing route data: " + resource);
        }
        Set<Integer> pairs = new HashSet<>();
        Map<Integer, Optional<RouteLeg>> routes = new HashMap<>();
        for (RouteEntry entry : file.routes) {
            Port from = Port.valueOf(entry.from);
            Port to = Port.valueOf(entry.to);
            int pair = Math.min(from.ordinal(), to.ordinal()) * portCount + Math.max(from.ordinal(), to.ordinal());
            if (from == to || !pairs.add(pair)) {
                throw new IllegalStateException("Duplicate sailing route pair: " + entry.from + " to " + entry.to);
            }
            if (entry.distance == null) {
                if (entry.points.length != 0) {
                    throw new IllegalStateException(
                            "Invalid unreachable sailing route: " + entry.from + " to " + entry.to);
                }
                routes.put(PortGraph.routeKey(from, to), Optional.empty());
                routes.put(PortGraph.routeKey(to, from), Optional.empty());
                continue;
            }
            if (entry.points.length < 2
                    || !point(entry.points[0]).equals(from.navigationLocation)
                    || !point(entry.points[entry.points.length - 1]).equals(to.navigationLocation)) {
                throw new IllegalStateException("Invalid sailing route endpoints: " + entry.from + " to " + entry.to);
            }
            List<WorldPoint> points = new ArrayList<>();
            for (int[] coordinates : entry.points) {
                points.add(point(coordinates));
            }
            RouteLeg route = new RouteLeg(from, to, entry.distance, points);
            routes.put(PortGraph.routeKey(from, to), Optional.of(route));
            Collections.reverse(points);
            routes.put(PortGraph.routeKey(to, from), Optional.of(new RouteLeg(to, from, entry.distance, points)));
        }
        return Map.copyOf(routes);
    }

    private static Map<List<Object>, Optional<RouteLeg>> portalRoutes(
            RouteFile file, BoatSize boatSize, String resource) {
        Set<List<Object>> expected = new HashSet<>();
        for (SailingShortcut shortcut : SailingShortcut.GWENITH) {
            for (Port port : Port.values()) {
                expected.add(List.of(
                        port.navigationLocation, shortcut.approach(boatSize), -1, shortcut.entryHeading, false));
                expected.add(List.of(
                        shortcut.departure(boatSize), port.navigationLocation, shortcut.exitHeading, -1, false));
            }
        }
        Map<List<Object>, Optional<RouteLeg>> routes = new HashMap<>();
        for (PortalEntry entry : file.portalRoutes) {
            WorldPoint from = point(entry.from);
            WorldPoint to = point(entry.to);
            List<Object> key = List.of(from, to, entry.departure, entry.arrival, false);
            if (!expected.remove(key)) {
                throw new IllegalStateException("Unexpected or duplicate portal route: " + resource);
            }
            if (entry.distance == null) {
                if (entry.points.length != 0) {
                    throw new IllegalStateException("Invalid unreachable portal route: " + resource);
                }
                routes.put(key, Optional.empty());
                continue;
            }
            if (entry.points.length < 2
                    || !point(entry.points[0]).equals(from)
                    || !point(entry.points[entry.points.length - 1]).equals(to)) {
                throw new IllegalStateException("Invalid portal route endpoints: " + resource);
            }
            List<WorldPoint> points = new ArrayList<>();
            for (int[] coordinates : entry.points) {
                points.add(point(coordinates));
            }
            routes.put(key, Optional.of(new RouteLeg(null, null, entry.distance, points)));
        }
        if (!expected.isEmpty()) {
            throw new IllegalStateException("Incomplete portal route data: " + resource);
        }
        return Map.copyOf(routes);
    }

    private static WorldPoint point(int[] coordinates) {
        return new WorldPoint(coordinates[0], coordinates[1], 0);
    }

    private static final class RouteFile {
        private int formatVersion;
        private String boatSize;
        private List<RouteEntry> routes;
        private List<PortalEntry> portalRoutes;
    }

    private static final class RouteEntry {
        private String from;
        private String to;
        private Double distance;
        private int[][] points = new int[0][];
    }

    private static final class PortalEntry {
        private int[] from;
        private int[] to;
        private int departure;
        private int arrival;
        private Double distance;
        private int[][] points;
    }
}
