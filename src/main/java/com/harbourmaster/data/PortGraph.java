package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.TravelStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

public final class PortGraph {
    private static final int ROUTE_FOLLOW_DISTANCE = 4;
    private final SailingRouter router;
    private final boolean background;
    private final Map<Integer, Optional<RouteLeg>> routes = new HashMap<>();
    private final Map<Port, Optional<RouteLeg>> boatRoutes = new EnumMap<>(Port.class);
    private final Map<PositionRouteKey, Optional<RouteLeg>> preparedBoatRoutes = new HashMap<>();
    private final Map<BoatSize, Map<Integer, Optional<RouteLeg>>> precomputedRoutes = new EnumMap<>(BoatSize.class);
    private final Map<BoatSize, Map<List<Object>, Optional<RouteLeg>>> precomputedPortalSegments =
            new EnumMap<>(BoatSize.class);
    private final Map<List<Object>, Optional<RouteLeg>> portalSegments = new HashMap<>();
    private final Map<Integer, Optional<RouteLeg>> shortcutRoutes = new HashMap<>();
    private final Map<Port, RouteLeg> shortcutBoatRoutes = new EnumMap<>(Port.class);
    private boolean shortcuts;

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
        portalSegments.clear();
        portalSegments.putAll(precomputedPortalSegments.getOrDefault(boatSize, Map.of()));
        shortcutRoutes.clear();
        shortcutBoatRoutes.clear();
        missingRoutes = false;
        return true;
    }

    public boolean hasMissingRoutes() {
        return missingRoutes;
    }

    public void clearMissingRoutes() {
        missingRoutes = false;
    }

    public void loadRoutes(
            BoatSize boatSize,
            Map<Integer, Optional<RouteLeg>> routes,
            Map<List<Object>, Optional<RouteLeg>> portalSegments) {
        precomputedRoutes.put(boatSize, routes);
        precomputedPortalSegments.put(boatSize, portalSegments);
        if (this.boatSize == boatSize) {
            this.routes.putAll(routes);
            this.portalSegments.putAll(portalSegments);
            shortcutRoutes.clear();
        }
    }

    public Optional<RouteLeg> routeFromPosition(WorldPoint position, Port destination) {
        RouteLeg previous = shortcutBoatRoutes.get(destination);
        Optional<RouteLeg> remaining =
                shortcuts && previous != null ? remainingShortcut(previous, position) : Optional.empty();
        // Skip direct searches only when even straight sailing cannot beat the cached journey.
        if (remaining.isEmpty()
                || distance(position, destination.navigationLocation) / 4
                        < remaining.get().travelTicks()) {
            Optional<RouteLeg> sailing = sailingFromPosition(position, destination);
            if (sailing.isPresent()
                    && (remaining.isEmpty()
                            || sailing.get().travelTicks() < remaining.get().travelTicks())) {
                remaining = sailing;
            }
        }
        Optional<RouteLeg> route = withShortcuts(null, position, destination, remaining);
        shortcutBoatRoutes.remove(destination);
        route.filter(leg -> !leg.sailingOnly()).ifPresent(leg -> shortcutBoatRoutes.put(destination, leg));
        return route;
    }

    private static Optional<RouteLeg> remainingShortcut(RouteLeg route, WorldPoint position) {
        RouteLeg best = null;
        for (int index = 0; index < route.steps.size(); index++) {
            TravelStep step = route.steps.get(index);
            if (step.kind != TravelStep.Kind.SAIL) {
                continue;
            }
            Optional<RouteLeg> remaining = nearbyRemainingRoute(new RouteLeg(null, route.to, 0, step.points), position);
            if (remaining.isEmpty()) {
                continue;
            }
            List<TravelStep> steps = new ArrayList<>();
            steps.add(new TravelStep(
                    TravelStep.Kind.SAIL, step.instruction, remaining.get().distance / 4 + 1, remaining.get().points));
            steps.addAll(route.steps.subList(index + 1, route.steps.size()));
            double distance = remaining.get().distance;
            for (TravelStep next : route.steps.subList(index + 1, route.steps.size())) {
                if (next.kind == TravelStep.Kind.SAIL) {
                    for (int point = 1; point < next.points.size(); point++) {
                        distance += distance(next.points.get(point - 1), next.points.get(point));
                    }
                }
            }
            RouteLeg candidate = new RouteLeg(null, route.to, distance, List.of(), steps);
            if (best == null || candidate.travelTicks() < best.travelTicks()) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<RouteLeg> sailingFromPosition(WorldPoint position, Port destination) {
        if (!position.equals(boatPosition)) {
            WorldPoint previousPosition = boatPosition;
            boatPosition = position;
            for (Port port : Port.values()) {
                Optional<RouteLeg> current = boatRoutes.get(port);
                if (current == null || !current.isPresent()) {
                    boatRoutes.remove(port);
                    continue;
                }
                Optional<RouteLeg> remaining = nearbyRemainingRoute(current.get(), position);
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
        Optional<RouteLeg> sailing = sailingRoute(from, to);
        if (!shortcuts || from == null || to == null || from == to) {
            return sailing;
        }
        int key = routeKey(from, to);
        Optional<RouteLeg> cached = shortcutRoutes.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<RouteLeg> route = withShortcuts(from, from.navigationLocation, to, sailing);
        if (!missingRoutes) {
            shortcutRoutes.put(key, route);
        }
        return route;
    }

    private Optional<RouteLeg> sailingRoute(Port from, Port to) {
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
        snapshot.shortcuts = shortcuts;
        snapshot.portalSegments.putAll(portalSegments);
        snapshot.shortcutRoutes.putAll(shortcutRoutes);
        snapshot.shortcutBoatRoutes.putAll(shortcutBoatRoutes);
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
        portalSegments.keySet().removeIf(key -> (Boolean) key.get(4));
        portalSegments.putAll(computed.portalSegments);
        shortcutRoutes.putAll(computed.shortcutRoutes);
        shortcutBoatRoutes.putAll(computed.shortcutBoatRoutes);
        if (computed.boatPosition != null) {
            for (Map.Entry<Port, Optional<RouteLeg>> entry : computed.boatRoutes.entrySet()) {
                preparedBoatRoutes.put(new PositionRouteKey(computed.boatPosition, entry.getKey()), entry.getValue());
            }
        }
        missingRoutes = false;
    }

    public boolean setShortcuts(boolean enabled) {
        if (shortcuts == enabled) {
            return false;
        }
        shortcuts = enabled;
        shortcutBoatRoutes.clear();
        return true;
    }

    private Optional<RouteLeg> withShortcuts(Port from, WorldPoint position, Port to, Optional<RouteLeg> direct) {
        if (!shortcuts || to == null) {
            return direct;
        }
        RouteLeg best = direct.orElse(null);
        for (SailingShortcut shortcut : SailingShortcut.GWENITH) {
            WorldPoint entry = shortcut.approach(boatSize);
            WorldPoint exit = shortcut.departure(boatSize);
            double minimum = (distance(position, entry) + distance(exit, to.navigationLocation)) / 4 + 4;
            if (best != null && minimum >= best.travelTicks()) {
                continue;
            }
            Optional<RouteLeg> approach = portalSegment(position, entry, -1, shortcut.entryHeading, from == null);
            Optional<RouteLeg> departure = portalSegment(exit, to.navigationLocation, shortcut.exitHeading, -1, false);
            if (approach.isEmpty() || departure.isEmpty()) {
                continue;
            }
            double distance = approach.get().distance + departure.get().distance;
            RouteLeg candidate = new RouteLeg(
                    from,
                    to,
                    distance,
                    List.of(),
                    List.of(
                            new TravelStep(
                                    TravelStep.Kind.SAIL,
                                    "Sail to the " + shortcut.name.toLowerCase(java.util.Locale.ROOT),
                                    approach.get().distance / 4 + 1,
                                    approach.get().points),
                            new TravelStep(
                                    TravelStep.Kind.PORTAL,
                                    "Enter the " + shortcut.name.toLowerCase(java.util.Locale.ROOT),
                                    2 + (distance(entry, shortcut.entrance) + distance(shortcut.exit, exit)) / 4,
                                    List.of(entry, exit)),
                            new TravelStep(
                                    TravelStep.Kind.SAIL,
                                    "Sail to " + to.name,
                                    departure.get().distance / 4 + 1,
                                    departure.get().points)));
            if (best == null || candidate.travelTicks() < best.travelTicks()) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<RouteLeg> portalSegment(
            WorldPoint from, WorldPoint to, int departure, int arrival, boolean moving) {
        List<Object> key = List.of(from, to, departure, arrival, moving);
        if (portalSegments.containsKey(key)) {
            return portalSegments.get(key);
        }
        if (moving) {
            Optional<RouteLeg> remaining = portalSegments.entrySet().stream()
                    .filter(entry -> entry.getKey().get(1).equals(to)
                            && entry.getKey().get(3).equals(arrival))
                    .map(Map.Entry::getValue)
                    .filter(Optional::isPresent)
                    .map(route -> nearbyRemainingRoute(route.get(), from))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .min(java.util.Comparator.comparingDouble(route -> route.distance));
            // Keep only the latest moving approach to each portal.
            portalSegments
                    .keySet()
                    .removeIf(cached -> (Boolean) cached.get(4) && cached.get(1).equals(to));
            if (remaining.isPresent()) {
                portalSegments.put(key, remaining);
                return remaining;
            }
        }
        if (background) {
            return portalSegments.computeIfAbsent(key, ignored -> router.route(from, to, boatSize, departure, arrival));
        }
        missingRoutes = true;
        return Optional.empty();
    }

    private Optional<RouteLeg> reusableBoatRoute(WorldPoint position, Port destination) {
        Optional<RouteLeg> prepared = preparedBoatRoutes.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(route -> route.to == destination)
                .map(route -> nearbyRemainingRoute(route, position))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(java.util.Comparator.comparingDouble(route -> route.distance));
        if (prepared.isPresent()) {
            return prepared;
        }
        Optional<RouteLeg> departure = portalSegments.entrySet().stream()
                .filter(entry -> entry.getKey().get(1).equals(destination.navigationLocation))
                .map(Map.Entry::getValue)
                .filter(Optional::isPresent)
                .map(route -> nearbyRemainingRoute(route.get(), position))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(java.util.Comparator.comparingDouble(route -> route.distance));
        if (departure.isPresent()) {
            return Optional.of(new RouteLeg(null, destination, departure.get().distance, departure.get().points));
        }
        return routes.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(route -> route.to == destination)
                .map(route -> nearbyRemainingRoute(route, position))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparing(
                                (RouteLeg route) -> !route.points.get(0).equals(position))
                        .thenComparingDouble(route -> route.distance));
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
            for (int next = index + 1; next < points.size(); next++) {
                distance += distance(points.get(next - 1), points.get(next));
            }
            return Optional.of(new RouteLeg(null, route.to, distance, remaining));
        }
        return Optional.empty();
    }

    private static Optional<RouteLeg> nearbyRemainingRoute(RouteLeg route, WorldPoint position) {
        Optional<RouteLeg> exact = remainingRoute(route, position);
        if (exact.isPresent()) {
            return exact;
        }
        WorldPoint closest = null;
        double closestDistance = ROUTE_FOLLOW_DISTANCE;
        for (int index = 1; index < route.points.size(); index++) {
            WorldPoint from = route.points.get(index - 1);
            WorldPoint to = route.points.get(index);
            int horizontal = to.getX() - from.getX();
            int vertical = to.getY() - from.getY();
            int steps = Math.max(Math.abs(horizontal), Math.abs(vertical));
            double fraction =
                    ((position.getX() - from.getX()) * horizontal + (position.getY() - from.getY()) * vertical)
                            / (double) (horizontal * horizontal + vertical * vertical);
            int step = (int) Math.round(Math.max(0, Math.min(1, fraction)) * steps);
            WorldPoint projected = new WorldPoint(
                    from.getX() + horizontal * step / steps,
                    from.getY() + vertical * step / steps,
                    position.getPlane());
            double deviation = distance(position, projected);
            if (deviation <= closestDistance && onSegment(projected, from, to)) {
                closest = projected;
                closestDistance = deviation;
            }
        }
        // Trim the checked route itself instead of drawing an unchecked shortcut from the boat.
        return closest == null ? Optional.empty() : remainingRoute(route, closest);
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
