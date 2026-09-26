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
import java.util.concurrent.CancellationException;
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
        List<Integer> taskLengths = new ArrayList<>();
        for (ActiveTask active : held) {
            if (active.definition == null) {
                return RoutePlan.unavailable("Unrecognized active task " + active.taskId);
            }
            if (active.isFinished()) {
                continue;
            }
            CourierTask task = active.definition;
            if (active.pickupRemaining() > 0) {
                events.add(new RouteEvent(
                        active.slot, task, RouteEvent.Action.PICKUP, task.pickup, active.pickupRemaining()));
            }
            events.add(new RouteEvent(
                    active.slot, task, RouteEvent.Action.DELIVER, task.delivery, active.deliveryRemaining()));
            taskLengths.add(active.pickupRemaining() > 0 ? 2 : 1);
        }
        for (CourierTask task : offers) {
            events.add(new RouteEvent(-1, task, RouteEvent.Action.ACCEPT, task.board, 0));
            events.add(new RouteEvent(-1, task, RouteEvent.Action.PICKUP, task.pickup, task.quantity));
            events.add(new RouteEvent(-1, task, RouteEvent.Action.DELIVER, task.delivery, task.quantity));
            taskLengths.add(3);
        }
        return optimize(start, boatPosition, events, taskLengths, firstStop, freeSlots, tasksUntilReset);
    }

    private RoutePlan optimize(
            Port start,
            WorldPoint boatPosition,
            List<RouteEvent> events,
            List<Integer> taskLengths,
            Port firstStop,
            int freeSlots,
            int tasksUntilReset) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Courier plan cancelled");
        }
        if (events.isEmpty()) {
            return RoutePlan.empty();
        }
        if (start == null && boatPosition == null) {
            return RoutePlan.unavailable("Visit a known port to establish the route start");
        }
        int count = events.size();
        int[] increments = new int[count];
        int[] offsets = new int[taskLengths.size()];
        int stateCount = 1;
        int offset = 0;
        for (int task = 0; task < taskLengths.size(); task++) {
            offsets[task] = offset;
            int length = taskLengths.get(task);
            Arrays.fill(increments, offset, offset + length, stateCount);
            stateCount *= length + 1;
            offset += length;
        }
        RouteLeg[][] journeys = new RouteLeg[count + 1][count];
        for (int destination = 0; destination < count; destination++) {
            journeys[count][destination] = journey(start, boatPosition, events.get(destination).port);
            for (int origin = 0; origin < count; origin++) {
                journeys[origin][destination] = graph.route(events.get(origin).port, events.get(destination).port)
                        .orElse(null);
            }
        }
        int positions = count + 1;
        double[] times = new double[stateCount * positions];
        int[] parents = new int[times.length];
        Arrays.fill(times, Double.POSITIVE_INFINITY);
        Arrays.fill(parents, -1);
        times[count] = 0;
        for (int state = 0; state < stateCount; state++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Courier plan cancelled");
            }
            int completedTasks = 0;
            int availableSlots = freeSlots;
            for (int task = 0; task < taskLengths.size(); task++) {
                int progress = state / increments[offsets[task]] % (taskLengths.get(task) + 1);
                if (progress == taskLengths.get(task)) {
                    completedTasks++;
                    availableSlots++;
                }
                if (progress > 0 && events.get(offsets[task]).action == RouteEvent.Action.ACCEPT) {
                    availableSlots--;
                }
            }
            for (int task = 0; task < taskLengths.size(); task++) {
                int progress = state / increments[offsets[task]] % (taskLengths.get(task) + 1);
                if (progress == taskLengths.get(task)) {
                    continue;
                }
                int next = offsets[task] + progress;
                RouteEvent event = events.get(next);
                if (event.action == RouteEvent.Action.ACCEPT
                        && (availableSlots == 0 || completedTasks >= tasksUntilReset)) {
                    continue;
                }
                if (state == 0 && firstStop != null && event.port != firstStop) {
                    continue;
                }
                int combined = (state + increments[next]) * positions + next;
                for (int previous = 0; previous < positions; previous++) {
                    double elapsed = times[state * positions + previous];
                    RouteLeg journey = journeys[previous][next];
                    if (!Double.isFinite(elapsed) || journey == null) {
                        continue;
                    }
                    double time = elapsed + CourierTimeModel.travelTicks(journey);
                    if (time < times[combined] - EPSILON) {
                        times[combined] = time;
                        parents[combined] = previous;
                    }
                }
            }
        }
        int complete = stateCount - 1;
        int last = -1;
        for (int event = 0; event < count; event++) {
            if (Double.isFinite(times[complete * positions + event])
                    && (last < 0
                            || times[complete * positions + event] < times[complete * positions + last] - EPSILON)) {
                last = event;
            }
        }
        if (last < 0) {
            return RoutePlan.unavailable("Route unavailable: unknown or disconnected port");
        }

        List<RouteEvent> ordered = new ArrayList<>();
        for (int state = complete; state > 0; ) {
            ordered.add(events.get(last));
            int parent = parents[state * positions + last];
            state -= increments[last];
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
                int positionInStop = grouped.size();
                if (event.action == RouteEvent.Action.ACCEPT) {
                    while (positionInStop > 0 && grouped.get(positionInStop - 1).action == RouteEvent.Action.PICKUP) {
                        positionInStop--;
                    }
                }
                grouped.add(positionInStop, event);
                stops.add(new RouteStop(event.port, grouped));
            }
        }
        return new RoutePlan(
                true, "", legs.stream().mapToDouble(leg -> leg.distance).sum(), legs, stops);
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
}
