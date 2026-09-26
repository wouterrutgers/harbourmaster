package com.harbourmaster.optimizer;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.model.RouteStop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class RouteOptimizer {
    private static final double EPSILON = 0.0000001;
    private final PortGraph graph;

    public RouteOptimizer(PortGraph graph) {
        this.graph = graph;
    }

    public RoutePlan optimize(Port start, List<ActiveTask> tasks) {
        return optimize(start, null, tasks, null);
    }

    public RoutePlan optimize(Port start, WorldPoint boatPosition, List<ActiveTask> tasks, Port firstStop) {
        return plan(start, boatPosition, tasks, List.of(), 0, 0, firstStop);
    }

    public RoutePlan optimizeWithOffers(
            Port start,
            WorldPoint boatPosition,
            List<ActiveTask> held,
            List<CourierTask> offers,
            int freeSlots,
            int tasksUntilReset) {
        return plan(start, boatPosition, held, offers, freeSlots, tasksUntilReset, null);
    }

    private RoutePlan plan(
            Port start,
            WorldPoint boatPosition,
            List<ActiveTask> held,
            List<CourierTask> offers,
            int freeSlots,
            int tasksUntilReset,
            Port firstStop) {
        List<RouteEvent> events = new ArrayList<>();
        List<Integer> prerequisites = new ArrayList<>();
        for (ActiveTask active : held) {
            if (active.definition == null) {
                return RoutePlan.unavailable("Unrecognized active task " + active.taskId);
            }
            if (active.isFinished()) {
                continue;
            }
            CourierTask task = active.definition;
            int pickupMask = 0;
            if (active.pickupRemaining() > 0) {
                pickupMask = 1 << events.size();
                events.add(new RouteEvent(
                        active.slot, task, RouteEvent.Action.PICKUP, task.pickup, active.pickupRemaining()));
                prerequisites.add(0);
            }
            events.add(new RouteEvent(
                    active.slot, task, RouteEvent.Action.DELIVER, task.delivery, active.deliveryRemaining()));
            prerequisites.add(pickupMask);
        }
        for (CourierTask task : offers) {
            int acceptance = 1 << events.size();
            events.add(new RouteEvent(-1, task, RouteEvent.Action.ACCEPT, task.board, 0));
            prerequisites.add(0);
            int pickup = 1 << events.size();
            events.add(new RouteEvent(-1, task, RouteEvent.Action.PICKUP, task.pickup, task.quantity));
            prerequisites.add(acceptance);
            events.add(new RouteEvent(-1, task, RouteEvent.Action.DELIVER, task.delivery, task.quantity));
            prerequisites.add(pickup);
        }
        return optimize(start, boatPosition, events, prerequisites, firstStop, freeSlots, tasksUntilReset);
    }

    private RoutePlan optimize(
            Port start,
            WorldPoint boatPosition,
            List<RouteEvent> events,
            List<Integer> prerequisites,
            Port firstStop,
            int freeSlots,
            int tasksUntilReset) {
        if (events.isEmpty()) {
            return RoutePlan.empty();
        }
        if (start == null && boatPosition == null) {
            return RoutePlan.unavailable("Visit a known port to establish the route start");
        }
        int count = events.size();
        int complete = (1 << count) - 1;
        RouteLeg[][] journeys = new RouteLeg[count + 1][count];
        for (int destination = 0; destination < count; destination++) {
            journeys[count][destination] = journey(start, boatPosition, events.get(destination).port);
            for (int origin = 0; origin < count; origin++) {
                journeys[origin][destination] = graph.route(events.get(origin).port, events.get(destination).port)
                        .orElse(null);
            }
        }
        double[][] distances = new double[complete + 1][count];
        int[][] parents = new int[complete + 1][count];
        int[][] sailings = new int[complete + 1][count];
        for (int mask = 0; mask <= complete; mask++) {
            Arrays.fill(distances[mask], Double.POSITIVE_INFINITY);
            Arrays.fill(parents[mask], -1);
        }
        int acceptances = 0;
        int deliveries = 0;
        for (int event = 0; event < count; event++) {
            if (events.get(event).action == RouteEvent.Action.ACCEPT) {
                acceptances |= 1 << event;
            }
            if (events.get(event).action == RouteEvent.Action.DELIVER) {
                deliveries |= 1 << event;
            }
        }
        for (int event = 0; event < count; event++) {
            RouteLeg journey = journeys[count][event];
            if (prerequisites.get(event) == 0
                    && journey != null
                    && (events.get(event).action != RouteEvent.Action.ACCEPT || freeSlots > 0 && tasksUntilReset > 0)
                    && (firstStop == null || events.get(event).port == firstStop)) {
                distances[1 << event][event] = journey.distance;
                sailings[1 << event][event] = journey.from == journey.to ? 0 : 1;
            }
        }
        for (int visited = 1; visited <= complete; visited++) {
            int completedTasks = Integer.bitCount(visited & deliveries);
            int availableSlots = freeSlots + completedTasks - Integer.bitCount(visited & acceptances);
            for (int previous = 0; previous < count; previous++) {
                if (!Double.isFinite(distances[visited][previous])) {
                    continue;
                }
                for (int next = 0; next < count; next++) {
                    int bit = 1 << next;
                    RouteLeg journey = journeys[previous][next];
                    if ((visited & bit) != 0
                            || (visited & prerequisites.get(next)) != prerequisites.get(next)
                            || (events.get(next).action == RouteEvent.Action.ACCEPT
                                    && (availableSlots == 0 || completedTasks >= tasksUntilReset))
                            || journey == null) {
                        continue;
                    }
                    int combined = visited | bit;
                    double distance = distances[visited][previous] + journey.distance;
                    int sailCount = sailings[visited][previous] + (journey.from == journey.to ? 0 : 1);
                    if (better(distance, sailCount, distances[combined][next], sailings[combined][next])) {
                        distances[combined][next] = distance;
                        sailings[combined][next] = sailCount;
                        parents[combined][next] = previous;
                    }
                }
            }
        }
        int last = -1;
        for (int event = 0; event < count; event++) {
            if (Double.isFinite(distances[complete][event])
                    && (last < 0
                            || better(
                                    distances[complete][event],
                                    sailings[complete][event],
                                    distances[complete][last],
                                    sailings[complete][last]))) {
                last = event;
            }
        }
        if (last < 0) {
            return RoutePlan.unavailable("Route unavailable: unknown or disconnected port");
        }

        double distance = distances[complete][last];
        List<RouteEvent> ordered = new ArrayList<>();
        for (int remaining = complete; last >= 0; ) {
            ordered.add(events.get(last));
            int parent = parents[remaining][last];
            remaining ^= 1 << last;
            last = parent;
        }
        Collections.reverse(ordered);
        List<RouteLeg> legs = new ArrayList<>();
        List<RouteStop> stops = new ArrayList<>();
        Port position = start;
        if (boatPosition != null) {
            legs.add(journey(start, boatPosition, ordered.get(0).port));
            position = ordered.get(0).port;
        }
        for (RouteEvent event : ordered) {
            if (position != event.port) {
                legs.add(graph.route(position, event.port).orElseThrow(IllegalStateException::new));
                position = event.port;
            }
            if (stops.isEmpty() || stops.get(stops.size() - 1).port != event.port) {
                stops.add(new RouteStop(event.port, List.of(event)));
            } else {
                List<RouteEvent> grouped = new ArrayList<>(stops.remove(stops.size() - 1).events);
                grouped.add(event);
                stops.add(new RouteStop(event.port, grouped));
            }
        }
        return new RoutePlan(true, "", distance, legs, stops);
    }

    public RoutePlan relocate(RoutePlan plan, Port start, WorldPoint boatPosition) {
        if (plan.stops.isEmpty()) {
            return RoutePlan.empty();
        }
        Port destination = plan.stops.get(0).port;
        RouteLeg approach = journey(start, boatPosition, destination);
        if (approach == null) {
            return RoutePlan.unavailable("Route unavailable: unknown or disconnected port");
        }
        List<RouteLeg> legs = new ArrayList<>(plan.legs);
        double distance = plan.distance;
        if (!legs.isEmpty() && legs.get(0).to == destination) {
            distance -= legs.remove(0).distance;
        }
        if (boatPosition != null || start != destination) {
            legs.add(0, approach);
            distance += approach.distance;
        }
        return new RoutePlan(true, "", distance, legs, plan.stops);
    }

    private RouteLeg journey(Port start, WorldPoint boatPosition, Port destination) {
        return (boatPosition == null
                        ? graph.route(start, destination)
                        : graph.routeFromPosition(boatPosition, destination))
                .orElse(null);
    }

    private static boolean better(double distance, int sailings, double existingDistance, int existingSailings) {
        return distance < existingDistance - EPSILON
                || (Math.abs(distance - existingDistance) < EPSILON && sailings < existingSailings);
    }
}
