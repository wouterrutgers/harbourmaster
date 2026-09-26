package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.DockChecklist;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.optimizer.RouteOptimizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class RouteTrackerTest {
    private final RouteTracker tracker = new RouteTracker(new RouteOptimizer(line()));

    @Test
    public void sailingPastAStopCannotAdvanceUntilAllItsDeliveriesAndPickupsFinish() {
        CourierTask unload = courier(1, A, B, 100);
        CourierTask load = courier(2, B, D, 100);
        List<ActiveTask> tasks = List.of(loaded(unload), accepted(load));
        assertSame(B, update(A, null, tasks).currentLeg.to);

        HarbourmasterSnapshot docked = update(B, B, tasks);
        assertSame(B, docked.nextPort());
        assertNull(docked.currentLeg);

        HarbourmasterSnapshot passed = update(D, null, tasks);
        assertSame(B, passed.currentLeg.to);
        assertSame(D, passed.currentLeg.from);

        tasks = List.of(new ActiveTask(1, unload.id, unload, 3, 1), new ActiveTask(2, load.id, load, 2, 0));
        HarbourmasterSnapshot partial = update(B, B, tasks);
        assertSame(B, partial.nextPort());
        assertNull(partial.currentLeg);

        tasks = List.of(new ActiveTask(1, unload.id, unload, 3, 3), new ActiveTask(2, load.id, load, 2, 0));
        HarbourmasterSnapshot stillLoading = update(B, B, tasks);
        assertSame(B, stillLoading.nextPort());
        assertNull(stillLoading.currentLeg);

        HarbourmasterSnapshot finished =
                update(B, B, List.of(new ActiveTask(1, unload.id, unload, 3, 3), loaded(load)));
        assertSame(D, finished.currentLeg.to);
        assertSame(B, finished.currentLeg.from);
    }

    @Test
    public void cancellingThePendingTaskReleasesItsStop() {
        ActiveTask remaining = loaded(courier(2, A, D, 100));
        assertSame(
                B,
                update(A, null, List.of(loaded(courier(1, A, B, 100)), remaining))
                        .nextPort());
        assertSame(D, update(D, D, List.of(remaining)).nextPort());
        assertTrue(update(D, D, List.of()).route.stops.isEmpty());
    }

    @Test
    public void acceptedTaskReplansToIncludeItsPickupBeforeLeavingTheCurrentPort() {
        ActiveTask held = loaded(courier(1, A, B, 100));
        assertSame(B, update(A, A, List.of(held)).nextPort());
        HarbourmasterSnapshot accepted = update(A, A, List.of(held, accepted(courier(2, A, D, 100))));
        assertSame(A, accepted.nextPort());
        assertNull(accepted.currentLeg);
    }

    @Test
    public void acceptingARemotePickupReordersTheRouteToReduceTotalDistance() {
        RouteTracker sailing = new RouteTracker(new RouteOptimizer(line()));
        List<ActiveTask> deliveries = List.of(
                loaded(courier(1, Port.PORT_ROBERTS, Port.DEEPFIN_POINT, 100)),
                loaded(courier(2, Port.PORT_ROBERTS, Port.DEEPFIN_POINT, 100)),
                loaded(courier(3, Port.PORT_ROBERTS, Port.DEEPFIN_POINT, 100)),
                loaded(courier(4, Port.PORT_ROBERTS, Port.DEEPFIN_POINT, 100)));
        ActiveTask newTask = accepted(courier(5, Port.PORT_PISCARILIUS, Port.PORT_ROBERTS, 100));
        assertSame(
                Port.DEEPFIN_POINT,
                sailing.update(Port.PORT_ROBERTS, deliveries).stops.get(0).port);

        List<ActiveTask> held = new ArrayList<>(deliveries);
        held.add(newTask);
        RoutePlan route = sailing.update(Port.PORT_ROBERTS, held);
        assertSame(Port.PORT_PISCARILIUS, route.stops.get(0).port);

        List<ActiveTask> carrying = new ArrayList<>(deliveries);
        carrying.add(loaded(newTask.definition));
        route = sailing.update(Port.PORT_PISCARILIUS, carrying);
        assertEquals(
                List.of(Port.PORT_ROBERTS, Port.DEEPFIN_POINT),
                route.stops.stream().map(stop -> stop.port).collect(Collectors.toList()));
    }

    @Test
    public void movingBoatUpdatesRemainingGeometryWithoutCompletingThePendingStop() {
        WorldPoint origin = new WorldPoint(100, 100, 0);
        WorldPoint bend = new WorldPoint(200, 100, 0);
        WorldPoint destination = new WorldPoint(200, 200, 0);
        WorldPoint firstBoatPosition = new WorldPoint(150, 100, 0);
        WorldPoint secondBoatPosition = new WorldPoint(200, 220, 0);
        RouteTracker moving = new RouteTracker(new RouteOptimizer(new PortGraph((from, to, boatSize) -> {
            if (from.equals(firstBoatPosition)) {
                return Optional.of(new RouteLeg(null, null, 150, List.of(firstBoatPosition, bend, destination)));
            }
            if (from.equals(secondBoatPosition)) {
                return Optional.of(new RouteLeg(null, null, 20, List.of(secondBoatPosition, destination)));
            }
            return Optional.of(new RouteLeg(null, null, 200, List.of(origin, bend, destination)));
        })));
        List<ActiveTask> tasks = List.of(loaded(courier(1, A, B, 100)));
        RoutePlan route = moving.update(A, firstBoatPosition, tasks);
        assertEquals(150, route.distance, 0.00001);
        assertEquals(List.of(new WorldPoint(150, 100, 0), bend, destination), route.legs.get(0).points);
        route = moving.update(A, secondBoatPosition, tasks);
        assertEquals(20, route.distance, 0.00001);
        assertEquals(new WorldPoint(200, 220, 0), route.legs.get(0).points.get(0));
        assertSame(B, route.stops.get(0).port);
        route = moving.update(A, null, tasks);
        assertEquals(200, route.distance, 0.00001);
        assertSame(A, route.legs.get(0).from);
    }

    @Test
    public void initialOrderingUsesBoatPositionRatherThanLastPort() {
        RouteTracker moving = new RouteTracker(new RouteOptimizer(line()));
        RoutePlan route = moving.update(
                A, B.navigationLocation, List.of(loaded(courier(1, B, A, 100)), loaded(courier(2, A, B, 100))));
        assertSame(B, route.stops.get(0).port);
        assertEquals(10, route.distance, 0.00001);
    }

    private HarbourmasterSnapshot update(Port start, Port dock, List<ActiveTask> tasks) {
        return new HarbourmasterSnapshot(
                true,
                tracker.update(start, tasks),
                List.of(),
                false,
                5 - tasks.size(),
                DockChecklist.at(dock, tasks),
                Map.of(),
                false);
    }
}
