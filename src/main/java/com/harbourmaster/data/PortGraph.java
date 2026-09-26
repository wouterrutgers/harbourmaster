package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.runelite.api.coords.WorldPoint;

public final class PortGraph {
    private static final int SEARCH_STATES_PER_SLICE = 4;
    private final SailingRouter router;
    private final boolean background;
    private final Map<Integer, Optional<RouteLeg>> routes = new HashMap<>();
    private final Map<Port, Optional<RouteLeg>> boatRoutes = new EnumMap<>(Port.class);
    private final Map<PositionRouteKey, Optional<RouteLeg>> preparedBoatRoutes = new HashMap<>();
    private final Map<RouteRequest, PendingRoute> pendingRoutes = new LinkedHashMap<>();
    private final Map<BoatSize, Map<Integer, Optional<RouteLeg>>> precomputedRoutes = new EnumMap<>(BoatSize.class);

    private BoatSize boatSize = BoatSize.SLOOP;
    private WorldPoint boatPosition;

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
        pendingRoutes.clear();
        return true;
    }

    public void advanceRouteSearches(long maximumNanos) {
        long startedAt = System.nanoTime();
        while (!pendingRoutes.isEmpty() && System.nanoTime() - startedAt < maximumNanos) {
            PendingRoute pending = pendingRoutes.values().iterator().next();
            if (!advance(pending, SEARCH_STATES_PER_SLICE)) {
                continue;
            }
            complete(pending);
        }
    }

    public boolean hasPendingRouteSearches() {
        return !pendingRoutes.isEmpty();
    }

    public void clearPendingRouteSearches() {
        pendingRoutes.clear();
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
            if (previousPosition == null) {
                boatRoutes.clear();
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
        if (pendingRoutes.values().stream().anyMatch(pending -> pending.from == null && pending.to == destination)) {
            return Optional.empty();
        }
        RouteRequest request = RouteRequest.forPosition(key);
        PendingRoute pending = pendingRoutes.get(request);
        if (pending == null) {
            pending = beginBoatRoute(key);
        }
        if (isFirstPending(pending) && advance(pending, SEARCH_STATES_PER_SLICE)) {
            complete(pending);
            return boatRoutes.computeIfAbsent(destination, ignored -> preparedBoatRoutes.get(key));
        }
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
        RouteRequest request = RouteRequest.forPorts(from, to);
        PendingRoute pending = pendingRoutes.get(request);
        if (pending == null) {
            pending = beginPortRoute(from, to);
        }
        if (isFirstPending(pending) && advance(pending, SEARCH_STATES_PER_SLICE)) {
            complete(pending);
            return routes.get(key);
        }
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
        pendingRoutes
                .entrySet()
                .removeIf(entry -> entry.getKey().positionKey == null
                        ? routes.containsKey(routeKey(entry.getKey().from, entry.getKey().to))
                        : preparedBoatRoutes.containsKey(entry.getKey().positionKey));
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

    private PendingRoute beginPortRoute(Port from, Port to) {
        RouteRequest request = RouteRequest.forPorts(from, to);
        PendingRoute pending = new PendingRoute(request, from, to, null);
        pendingRoutes.put(request, pending);
        return pending;
    }

    private PendingRoute beginBoatRoute(PositionRouteKey key) {
        RouteRequest request = RouteRequest.forPosition(key);
        PendingRoute pending = new PendingRoute(request, null, key.port, key);
        pendingRoutes.put(request, pending);
        return pending;
    }

    private boolean isFirstPending(PendingRoute pending) {
        return pendingRoutes.values().iterator().next() == pending;
    }

    private boolean advance(PendingRoute pending, int maximumExpandedStates) {
        if (pending.search == null) {
            WorldPoint from =
                    pending.positionKey == null ? pending.from.navigationLocation : pending.positionKey.position;
            pending.search = router.search(from, pending.to.navigationLocation, boatSize);
        }
        return pending.search.advance(maximumExpandedStates);
    }

    private void complete(PendingRoute pending) {
        Optional<RouteLeg> result = pending.search
                .result()
                .map(route -> new RouteLeg(pending.from, pending.to, route.distance, route.points));
        if (pending.positionKey == null) {
            cacheRoute(pending.from, pending.to, result);
        } else {
            preparedBoatRoutes.put(pending.positionKey, result);
        }
        pendingRoutes.remove(pending.request);
    }

    private void cacheRoute(Port from, Port to, Optional<RouteLeg> route) {
        routes.put(routeKey(from, to), route);
        routes.put(routeKey(to, from), route.map(forward -> {
            List<WorldPoint> points = new ArrayList<>(forward.points);
            Collections.reverse(points);
            return new RouteLeg(to, from, forward.distance, points);
        }));
        pendingRoutes.remove(RouteRequest.forPorts(to, from));
    }

    private Optional<RouteLeg> searchRoute(Port from, WorldPoint position, Port to) {
        SailingSearch search = router.search(position, to.navigationLocation, boatSize);
        while (!search.advance(Integer.MAX_VALUE)) {}
        return search.result().map(route -> new RouteLeg(from, to, route.distance, route.points));
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

    private static final class PendingRoute {
        private final RouteRequest request;
        private final Port from;
        private final Port to;
        private final PositionRouteKey positionKey;
        private SailingSearch search;

        private PendingRoute(RouteRequest request, Port from, Port to, PositionRouteKey positionKey) {
            this.request = request;
            this.from = from;
            this.to = to;
            this.positionKey = positionKey;
        }
    }

    private static final class RouteRequest {
        private final Port from;
        private final PositionRouteKey positionKey;
        private final Port to;

        private RouteRequest(Port from, PositionRouteKey positionKey, Port to) {
            this.from = from;
            this.positionKey = positionKey;
            this.to = to;
        }

        private static RouteRequest forPorts(Port from, Port to) {
            return new RouteRequest(from, null, to);
        }

        private static RouteRequest forPosition(PositionRouteKey key) {
            return new RouteRequest(null, key, key.port);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RouteRequest)) {
                return false;
            }
            RouteRequest request = (RouteRequest) other;
            return from == request.from
                    && java.util.Objects.equals(positionKey, request.positionKey)
                    && to == request.to;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(from, positionKey, to);
        }
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
