package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.runelite.api.coords.WorldPoint;

public final class PortGraph {
    private final SailingRouter router;
    private final boolean background;
    private final Map<Integer, Optional<RouteLeg>> routes = new HashMap<>();
    private final Map<Port, Optional<RouteLeg>> boatRoutes = new EnumMap<>(Port.class);
    private final Map<PositionRouteKey, Optional<RouteLeg>> preparedBoatRoutes = new HashMap<>();
    private final Map<BoatSize, Map<Integer, Optional<RouteLeg>>> precomputedRoutes = new EnumMap<>(BoatSize.class);

    private BoatSize boatSize = BoatSize.SLOOP;
    private WorldPoint boatPosition;
    private boolean missingRoutes;

    public PortGraph(SailingRouter router) {
        this(router, BoatSize.SLOOP, false);
    }

    private PortGraph(SailingRouter router, BoatSize boatSize, boolean background) {
        this.router = router;
        this.boatSize = boatSize;
        this.background = background;
    }

    public boolean setBoatSize(BoatSize boatSize) {
        if (this.boatSize == boatSize) {
            return false;
        }
        this.boatSize = boatSize;
        routes.clear();
        routes.putAll(precomputedRoutes.getOrDefault(boatSize, Map.of()));
        boatRoutes.clear();
        preparedBoatRoutes.clear();
        missingRoutes = false;
        return true;
    }

    public boolean hasMissingRoutes() {
        return missingRoutes;
    }

    public void clearMissingRoutes() {
        missingRoutes = false;
    }

    public void loadPortRoutes(BoatSize boatSize, Map<Integer, Optional<RouteLeg>> routes) {
        precomputedRoutes.put(boatSize, routes);
        if (this.boatSize == boatSize) {
            this.routes.putAll(routes);
        }
    }

    public Optional<RouteLeg> routeFromPosition(WorldPoint position, Port destination) {
        if (!position.equals(boatPosition)) {
            WorldPoint previousPosition = boatPosition;
            boatPosition = position;
            for (Port port : Port.values()) {
                Optional<RouteLeg> current = boatRoutes.get(port);
                if (current == null || !current.isPresent()) {
                    boatRoutes.remove(port);
                    continue;
                }
                Optional<RouteLeg> remaining = remainingRoute(current.get(), position);
                if (remaining.isPresent()) {
                    boatRoutes.put(port, remaining);
                    preparedBoatRoutes.put(new PositionRouteKey(position, port), remaining);
                    continue;
                }
                boatRoutes.remove(port);
            }
            preparedBoatRoutes
                    .keySet()
                    .removeIf(key -> !key.position.equals(position)
                            && (previousPosition == null || !key.position.equals(previousPosition)));
        }
        PositionRouteKey key = new PositionRouteKey(position, destination);
        Optional<RouteLeg> prepared = preparedBoatRoutes.get(key);
        if (prepared != null) {
            return boatRoutes.computeIfAbsent(destination, ignored -> prepared);
        }
        Optional<RouteLeg> cached = boatRoutes.get(destination);
        if (cached != null) {
            return cached;
        }
        Optional<RouteLeg> reusable = reusableBoatRoute(position, destination);
        if (reusable.isPresent()) {
            preparedBoatRoutes.put(key, reusable);
            boatRoutes.put(destination, reusable);
            return reusable;
        }
        if (background) {
            return boatRoutes.computeIfAbsent(destination, port -> searchRoute(null, position, port));
        }
        missingRoutes = true;
        return Optional.empty();
    }

    public Optional<RouteLeg> route(Port from, Port to) {
        if (from == null || to == null) {
            return Optional.empty();
        }
        if (from == to) {
            return Optional.of(new RouteLeg(from, to, 0, List.of(from.navigationLocation)));
        }
        int key = routeKey(from, to);
        if (routes.containsKey(key)) {
            return routes.get(key);
        }
        if (background) {
            Optional<RouteLeg> route = searchRoute(from, from.navigationLocation, to);
            cacheRoute(from, to, route);
            return route;
        }
        missingRoutes = true;
        return Optional.empty();
    }

    public PortGraph detachedSnapshot(WorldPoint position) {
        PortGraph snapshot = new PortGraph(router, boatSize, true);
        snapshot.routes.putAll(routes);
        snapshot.preparedBoatRoutes.putAll(preparedBoatRoutes);
        if (position != null) {
            for (Port port : Port.values()) {
                Optional<RouteLeg> route = preparedBoatRoutes.get(new PositionRouteKey(position, port));
                if (route == null && position.equals(boatPosition)) {
                    route = boatRoutes.get(port);
                }
                if (route != null) {
                    snapshot.boatRoutes.put(port, route);
                }
            }
        }
        snapshot.boatPosition = position;
        return snapshot;
    }

    public void mergeComputedRoutes(PortGraph computed) {
        routes.putAll(computed.routes);
        if (computed.boatPosition != null) {
            for (Map.Entry<Port, Optional<RouteLeg>> entry : computed.boatRoutes.entrySet()) {
                preparedBoatRoutes.put(new PositionRouteKey(computed.boatPosition, entry.getKey()), entry.getValue());
            }
        }
        missingRoutes = false;
    }

    private Optional<RouteLeg> reusableBoatRoute(WorldPoint position, Port destination) {
        return Stream.concat(preparedBoatRoutes.values().stream(), routes.values().stream())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(route -> route.to == destination)
                .map(route -> remainingRoute(route, position))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private void cacheRoute(Port from, Port to, Optional<RouteLeg> route) {
        routes.put(routeKey(from, to), route);
        routes.put(routeKey(to, from), route.map(forward -> {
            List<WorldPoint> points = new ArrayList<>(forward.points);
            Collections.reverse(points);
            return new RouteLeg(to, from, forward.distance, points);
        }));
    }

    private Optional<RouteLeg> searchRoute(Port from, WorldPoint position, Port to) {
        return router.route(position, to.navigationLocation, boatSize)
                .map(route -> new RouteLeg(from, to, route.distance, route.points));
    }

    private static Optional<RouteLeg> remainingRoute(RouteLeg route, WorldPoint position) {
        List<WorldPoint> points = route.points;
        if (points.size() == 1 && points.get(0).equals(position)) {
            return Optional.of(new RouteLeg(null, route.to, 0, List.of(position)));
        }
        for (int index = 1; index < points.size(); index++) {
            WorldPoint from = points.get(index - 1);
            WorldPoint to = points.get(index);
            if (!onSegment(position, from, to)) {
                continue;
            }
            List<WorldPoint> remaining = new ArrayList<>();
            remaining.add(position);
            if (!position.equals(to)) {
                remaining.add(to);
            }
            remaining.addAll(points.subList(index + 1, points.size()));
            double distance = distance(position, to);
            for (int next = index + 2; next < points.size(); next++) {
                distance += distance(points.get(next - 1), points.get(next));
            }
            return Optional.of(new RouteLeg(null, route.to, distance, remaining));
        }
        return Optional.empty();
    }

    private static boolean onSegment(WorldPoint point, WorldPoint from, WorldPoint to) {
        if (point.getPlane() != from.getPlane() || point.getPlane() != to.getPlane()) {
            return false;
        }
        int horizontal = to.getX() - from.getX();
        int vertical = to.getY() - from.getY();
        int pointHorizontal = point.getX() - from.getX();
        int pointVertical = point.getY() - from.getY();
        return pointHorizontal * vertical == pointVertical * horizontal
                && pointHorizontal * horizontal + pointVertical * vertical >= 0
                && pointHorizontal * horizontal + pointVertical * vertical
                        <= horizontal * horizontal + vertical * vertical;
    }

    private static double distance(WorldPoint from, WorldPoint to) {
        return Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
    }

    static int routeKey(Port from, Port to) {
        return from.ordinal() * Port.values().length + to.ordinal();
    }

    private static final class PositionRouteKey {
        private final WorldPoint position;
        private final Port port;

        private PositionRouteKey(WorldPoint position, Port port) {
            this.position = position;
            this.port = port;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PositionRouteKey)) {
                return false;
            }
            PositionRouteKey key = (PositionRouteKey) other;
            return position.equals(key.position) && port == key.port;
        }

        @Override
        public int hashCode() {
            return 31 * position.hashCode() + port.hashCode();
        }
    }
}
