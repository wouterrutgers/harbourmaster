package com.harbourmaster.optimizer;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.RoutePlan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class RouteOptimizerTest {
    private final RouteOptimizer optimizer = new RouteOptimizer(line());

    @Test
    public void singleTaskReconstructsPickupDeliveryAndTravelDistance() {
        RoutePlan plan = optimizer.optimize(A, List.of(accepted(courier(1, B, D, 100))));
        assertEquals(30, plan.distance, 0.00001);
        assertEquals(List.of(RouteEvent.Action.PICKUP, RouteEvent.Action.DELIVER), actions(plan));
        assertEquals(List.of(B, D), ports(plan));
        assertEquals(
                plan.distance,
                plan.legs.stream().mapToDouble(leg -> leg.distance).sum(),
                0.00001);
    }

    @Test
    public void cannotDeliverBeforePickupEvenWhenStandingAtDelivery() {
        RoutePlan plan = optimizer.optimize(A, List.of(accepted(courier(1, D, A, 100))));
        assertEquals(List.of(D, A), ports(plan));
        assertEquals(60, plan.distance, 0.00001);
    }

    @Test
    public void fullyLoadedCargoNeedsOnlyImmediateDelivery() {
        RoutePlan plan = optimizer.optimize(A, List.of(loaded(courier(1, D, A, 100))));
        assertEquals(0, plan.distance, 0);
        assertEquals(List.of(RouteEvent.Action.DELIVER), actions(plan));
        assertTrue(plan.legs.isEmpty());
    }

    @Test
    public void pickupAndDeliveryAtSamePortDoNotCauseReturnTrip() {
        RoutePlan plan =
                optimizer.optimize(A, List.of(accepted(courier(1, A, B, 100)), accepted(courier(2, B, D, 100))));
        assertEquals(30, plan.distance, 0.00001);
        assertEquals(
                List.of(A, B, D), plan.stops.stream().map(stop -> stop.port).collect(Collectors.toList()));
        assertEquals(2, plan.stops.get(1).events.size());
    }

    @Test
    public void partialProgressOnlyPlansOutstandingCratesAndSkipsCompletedTasks() {
        CourierTask task = courier(1, B, D, 100);
        RoutePlan plan = optimizer.optimize(
                A, List.of(new ActiveTask(0, 1, task, 2, 1), new ActiveTask(1, 2, courier(2, E, C, 100), 3, 3)));
        assertEquals(
                List.of(1, 2),
                plan.stops.stream()
                        .flatMap(stop -> stop.events.stream())
                        .map(event -> event.quantity)
                        .collect(Collectors.toList()));
    }

    @Test
    public void boardingTimeCanMakeASlightlyLongerSailingRouteFaster() {
        Map<WorldPoint, WorldPoint> positions = Map.of(
                A.navigationLocation, new WorldPoint(7, 14, 0),
                B.navigationLocation, new WorldPoint(17, 14, 0),
                C.navigationLocation, new WorldPoint(20, 5, 0),
                D.navigationLocation, new WorldPoint(17, 1, 0));
        PortGraph graph = new PortGraph((from, to, size) -> Optional.of(new RouteLeg(
                        null,
                        null,
                        Math.hypot(
                                positions.get(to).getX() - positions.get(from).getX(),
                                positions.get(to).getY() - positions.get(from).getY()),
                        List.of(from, to))))
                .detachedSnapshot(null);

        RoutePlan plan = new RouteOptimizer(graph)
                .optimize(
                        A,
                        List.of(
                                accepted(courier(1, B, D, 100)),
                                accepted(courier(2, B, D, 100)),
                                accepted(courier(3, C, B, 100))));

        assertEquals(
                List.of(C, B, D), plan.stops.stream().map(stop -> stop.port).collect(Collectors.toList()));
    }

    @Test
    public void optimumAgreesWithIndependentExhaustiveSearch() {
        Random random = new Random(20260921);
        Port[] ports = {A, B, C, D, E};
        for (int example = 0; example < 20; example++) {
            List<ActiveTask> tasks = new ArrayList<>();
            for (int id = 1; id <= 3; id++) {
                Port pickup = ports[random.nextInt(ports.length)];
                Port delivery = ports[random.nextInt(ports.length)];
                tasks.add(accepted(courier(id, pickup, delivery, 100)));
            }
            Port start = ports[random.nextInt(ports.length)];
            assertEquals(
                    brute(line(), start, tasks, new HashSet<>(), new HashSet<>()),
                    optimizer.optimize(start, tasks).legs.stream()
                            .mapToDouble(leg -> leg.distance / 4 + 2)
                            .sum(),
                    0.00001);
        }
    }

    private static double brute(
            PortGraph graph, Port from, List<ActiveTask> tasks, Set<Integer> picked, Set<Integer> delivered) {
        if (delivered.size() == tasks.size()) {
            return 0;
        }
        double best = Double.POSITIVE_INFINITY;
        for (ActiveTask active : tasks) {
            CourierTask task = active.definition;
            if (delivered.contains(task.id)) {
                continue;
            }
            boolean pickup = !picked.contains(task.id);
            Port destination = pickup ? task.pickup : task.delivery;
            Set<Integer> changed = pickup ? picked : delivered;
            changed.add(task.id);
            best = Math.min(
                    best,
                    graph.route(from, destination).get().distance / 4
                            + (from == destination ? 0 : 2)
                            + brute(graph, destination, tasks, picked, delivered));
            changed.remove(task.id);
        }
        return best;
    }

    private static List<Port> ports(RoutePlan plan) {
        return plan.stops.stream()
                .flatMap(stop -> stop.events.stream())
                .map(event -> event.port)
                .collect(Collectors.toList());
    }

    private static List<RouteEvent.Action> actions(RoutePlan plan) {
        return plan.stops.stream()
                .flatMap(stop -> stop.events.stream())
                .map(event -> event.action)
                .collect(Collectors.toList());
    }
}
