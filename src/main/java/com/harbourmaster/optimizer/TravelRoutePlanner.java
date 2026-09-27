package com.harbourmaster.optimizer;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.model.RouteStop;
import com.harbourmaster.model.TravelContext;
import com.harbourmaster.model.TravelMethod;
import com.harbourmaster.model.TravelStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import net.runelite.api.coords.WorldPoint;

final class TravelRoutePlanner {
    private final PortGraph graph;
    private final TravelContext travel;
    private final Map<List<Object>, List<Journey>> playerRoutes = new HashMap<>();

    TravelRoutePlanner(PortGraph graph, TravelContext travel) {
        this.graph = graph;
        this.travel = travel;
    }

    RoutePlan plan(
            Port start,
            WorldPoint boatPosition,
            List<ActiveTask> held,
            List<RouteEvent> events,
            List<Integer> taskLengths,
            Port firstStop,
            int freeSlots,
            int tasksUntilReset) {
        if (start == null || travel.boat == 0 || travel.boatPort == null && boatPosition == null) {
            return RoutePlan.unavailable("Board your courier boat to establish its location");
        }
        if (events.isEmpty()) {
            return RoutePlan.empty();
        }
        held = held.stream().filter(task -> !task.isFinished()).collect(java.util.stream.Collectors.toList());
        int[] offsets = new int[taskLengths.size()];
        int[] increments = new int[taskLengths.size()];
        int stateCount = 1;
        int offset = 0;
        for (int task = 0; task < taskLengths.size(); task++) {
            offsets[task] = offset;
            increments[task] = stateCount;
            offset += taskLengths.get(task);
            stateCount *= taskLengths.get(task) + 1;
        }
        Map<Integer, List<Visit>> states = new HashMap<>();
        Map<Integer, Integer> supplies = new HashMap<>();
        for (TravelMethod method : java.util.stream.Stream.concat(travel.methods.stream(), travel.summons.stream())
                .collect(java.util.stream.Collectors.toList())) {
            method.cost.forEach((item, quantity) -> supplies.merge(item, quantity * events.size() * 3, Math::max));
        }
        // A journey uses at most a teleport, a charter and a summon. Larger stocks cannot run out.
        supplies.entrySet().removeIf(entry -> travel.supplies.getOrDefault(entry.getKey(), 0) >= entry.getValue());
        supplies.replaceAll((item, maximum) -> travel.supplies.getOrDefault(item, 0));
        states.put(
                0,
                new ArrayList<>(List.of(new Visit(start, travel.boatPort, Map.copyOf(supplies), 0, null, null, null))));
        for (int state = 0; state < stateCount - 1; state++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Courier plan cancelled");
            }
            List<Visit> visits = states.remove(state);
            if (visits == null) {
                continue;
            }
            int slots = freeSlots;
            int completed = 0;
            boolean loaded = false;
            int heldIndex = 0;
            for (int task = 0; task < taskLengths.size(); task++) {
                int progress = state / increments[task] % (taskLengths.get(task) + 1);
                boolean offer = events.get(offsets[task]).action == RouteEvent.Action.ACCEPT;
                if (progress == taskLengths.get(task)) {
                    slots++;
                    completed++;
                } else if (offer) {
                    loaded |= progress == 2;
                } else {
                    loaded |= progress > 0 || held.get(heldIndex).carried() > 0;
                }
                if (!offer) {
                    heldIndex++;
                }
                if (offer && progress > 0) {
                    slots--;
                }
            }
            for (int task = 0; task < taskLengths.size(); task++) {
                int progress = state / increments[task] % (taskLengths.get(task) + 1);
                if (progress == taskLengths.get(task)) {
                    continue;
                }
                RouteEvent event = events.get(offsets[task] + progress);
                if (event.action == RouteEvent.Action.ACCEPT && (slots == 0 || completed >= tasksUntilReset)
                        || state == 0 && firstStop != null && event.port != firstStop) {
                    continue;
                }
                List<Visit> next = states.computeIfAbsent(state + increments[task], ignored -> new ArrayList<>());
                for (Visit visit : visits) {
                    for (Journey journey : journeys(visit, event, loaded, state == 0, boatPosition)) {
                        add(
                                next,
                                new Visit(
                                        event.port,
                                        journey.boat,
                                        journey.supplies,
                                        visit.ticks + journey.leg.travelTicks(),
                                        visit,
                                        event,
                                        journey.leg));
                    }
                }
            }
        }
        Visit best = states.getOrDefault(stateCount - 1, List.of()).stream()
                .min(java.util.Comparator.comparingDouble(visit -> visit.ticks))
                .orElse(null);
        if (best == null) {
            return RoutePlan.unavailable("No complete route with your available travel methods and supplies");
        }
        List<Visit> ordered = new ArrayList<>();
        for (Visit visit = best; visit.previous != null; visit = visit.previous) {
            ordered.add(visit);
        }
        Collections.reverse(ordered);
        List<RouteLeg> legs = new ArrayList<>();
        List<RouteStop> stops = new ArrayList<>();
        for (Visit visit : ordered) {
            if (!visit.leg.steps.isEmpty()) {
                legs.add(visit.leg);
            }
            if (stops.isEmpty() || stops.get(stops.size() - 1).port != visit.player || !visit.leg.steps.isEmpty()) {
                stops.add(new RouteStop(visit.player, List.of(visit.event), visit.leg));
                continue;
            }
            RouteStop previous = stops.remove(stops.size() - 1);
            List<RouteEvent> grouped = new ArrayList<>(previous.events);
            grouped.add(visit.event);
            stops.add(new RouteStop(visit.player, grouped, previous.arrival));
        }
        return new RoutePlan(
                true, "", legs.stream().mapToDouble(leg -> leg.distance).sum(), legs, stops);
    }

    private List<Journey> journeys(
            Visit visit, RouteEvent event, boolean loaded, boolean initial, WorldPoint boatPosition) {
        List<Journey> result = new ArrayList<>();
        boolean canLeave = !travel.carryingCargo && visit.boat != null;
        if (event.action == RouteEvent.Action.ACCEPT) {
            if (visit.player == event.port && visit.boat != null) {
                result.add(new Journey(visit.boat, visit.supplies, empty(visit.player)));
            } else if (canLeave) {
                result.addAll(playerJourneys(visit.player, event.port, visit.boat, visit.supplies));
            }
        }
        // A cargo action always takes place with the courier boat at this port.
        if (visit.boat == event.port && visit.player == event.port) {
            result.add(new Journey(visit.boat, visit.supplies, empty(visit.player)));
            return result;
        }
        List<Journey> returns = new ArrayList<>();
        if (visit.player == visit.boat || visit.boat == null && initial && travel.aboard) {
            returns.add(new Journey(visit.boat, visit.supplies, empty(visit.player)));
        } else if (canLeave) {
            returns.addAll(playerJourneys(visit.player, visit.boat, visit.boat, visit.supplies));
        }
        RouteLeg sailing = (visit.boat == null
                        ? graph.routeFromPosition(boatPosition, event.port)
                        : graph.route(visit.boat, event.port))
                .orElse(null);
        if (sailing != null) {
            for (Journey back : returns) {
                result.add(new Journey(event.port, back.supplies, join(visit.player, event.port, back.leg, sailing)));
            }
        }
        if (!loaded && travel.summonSafe && canLeave && visit.boat != event.port) {
            for (Journey outward : playerJourneys(visit.player, event.port, visit.boat, visit.supplies)) {
                for (TravelMethod summon : travel.summons) {
                    Map<Integer, Integer> supplies = spend(outward.supplies, summon.cost);
                    if (supplies != null) {
                        result.add(new Journey(
                                event.port,
                                supplies,
                                join(visit.player, event.port, outward.leg, summon.leg(event.port, event.port))));
                    }
                }
            }
        }
        return result;
    }

    private List<Journey> playerJourneys(Port from, Port to, Port boat, Map<Integer, Integer> supplies) {
        return playerRoutes.computeIfAbsent(
                List.of(from, to, boat, supplies), ignored -> findPlayerJourneys(from, to, boat, supplies));
    }

    private List<Journey> findPlayerJourneys(Port from, Port to, Port boat, Map<Integer, Integer> supplies) {
        if (from == to) {
            return List.of(new Journey(boat, supplies, empty(from)));
        }
        List<Journey> result = new ArrayList<>();
        for (TravelMethod method : travel.methods) {
            Port destination = method.boat == travel.boat && method.boat != 0 ? boat : method.destination;
            if (destination == null || method.origin != null && method.origin != from) {
                continue;
            }
            Map<Integer, Integer> remaining = spend(supplies, method.cost);
            if (remaining == null) {
                continue;
            }
            RouteLeg first = method.leg(from, destination);
            if (destination == to) {
                result.add(new Journey(boat, remaining, first));
                continue;
            }
            if (method.kind != TravelStep.Kind.TELEPORT) {
                continue;
            }
            // The wiki routes also use an amulet teleport to reach a charter crew.
            for (TravelMethod charter : travel.methods) {
                if (charter.kind != TravelStep.Kind.CHARTER
                        || charter.origin != destination
                        || charter.destination != to) {
                    continue;
                }
                Map<Integer, Integer> afterCharter = spend(remaining, charter.cost);
                if (afterCharter != null) {
                    result.add(new Journey(boat, afterCharter, join(from, to, first, charter.leg(destination, to))));
                }
            }
        }
        return result;
    }

    private static Map<Integer, Integer> spend(Map<Integer, Integer> supplies, Map<Integer, Integer> cost) {
        for (Map.Entry<Integer, Integer> entry : cost.entrySet()) {
            if (supplies.containsKey(entry.getKey()) && supplies.get(entry.getKey()) < entry.getValue()) {
                return null;
            }
        }
        Map<Integer, Integer> remaining = new HashMap<>(supplies);
        cost.forEach(
                (item, quantity) -> remaining.computeIfPresent(item, (ignored, available) -> available - quantity));
        return Map.copyOf(remaining);
    }

    private static void add(List<Visit> visits, Visit candidate) {
        for (Visit visit : visits) {
            if (visit.player == candidate.player && visit.boat == candidate.boat && dominates(visit, candidate)) {
                return;
            }
        }
        visits.removeIf(visit ->
                visit.player == candidate.player && visit.boat == candidate.boat && dominates(candidate, visit));
        visits.add(candidate);
    }

    private static boolean dominates(Visit first, Visit second) {
        if (first.ticks > second.ticks) {
            return false;
        }
        for (Map.Entry<Integer, Integer> entry : second.supplies.entrySet()) {
            if (first.supplies.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static RouteLeg empty(Port port) {
        return new RouteLeg(port, port, 0, List.of(), List.of());
    }

    private static RouteLeg join(Port from, Port to, RouteLeg first, RouteLeg second) {
        if (first.steps.isEmpty() && second.from == from && second.to == to) {
            return second;
        }
        List<TravelStep> steps = new ArrayList<>(first.steps);
        if (second.from != second.to || second.distance > 0 || !second.sailingOnly()) {
            steps.addAll(second.steps);
        }
        return new RouteLeg(from, to, first.distance + second.distance, List.of(), steps);
    }

    private static final class Journey {
        final Port boat;
        final Map<Integer, Integer> supplies;
        final RouteLeg leg;

        Journey(Port boat, Map<Integer, Integer> supplies, RouteLeg leg) {
            this.boat = boat;
            this.supplies = supplies;
            this.leg = leg;
        }
    }

    private static final class Visit {
        final Port player;
        final Port boat;
        final Map<Integer, Integer> supplies;
        final double ticks;
        final Visit previous;
        final RouteEvent event;
        final RouteLeg leg;

        Visit(
                Port player,
                Port boat,
                Map<Integer, Integer> supplies,
                double ticks,
                Visit previous,
                RouteEvent event,
                RouteLeg leg) {
            this.player = player;
            this.boat = boat;
            this.supplies = supplies;
            this.ticks = ticks;
            this.previous = previous;
            this.event = event;
            this.leg = leg;
        }
    }
}
