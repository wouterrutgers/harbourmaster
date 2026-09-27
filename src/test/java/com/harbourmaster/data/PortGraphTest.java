package com.harbourmaster.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.TravelStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PortGraphTest {
    @Test
    public void portalRouteKeepsSeparateSailingPathsAndAdvancesAfterTheJumpWithoutSearchingAgain() {
        SailingShortcut shortcut = SailingShortcut.GWENITH.get(0);
        WorldPoint entry = shortcut.approach(BoatSize.SKIFF);
        WorldPoint start = new WorldPoint(entry.getX(), entry.getY() + 40, 0);
        int[] searches = {0};
        PortGraph graph = new PortGraph(new SailingRouter() {
            @Override
            public Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize boatSize) {
                searches[0]++;
                return Optional.of(new RouteLeg(null, null, 10000, List.of(from, to)));
            }

            @Override
            public Optional<RouteLeg> route(
                    WorldPoint from, WorldPoint to, BoatSize boatSize, int departure, int arrival) {
                searches[0]++;
                if (arrival != -1) {
                    return Optional.of(new RouteLeg(null, null, from.distanceTo2D(to), List.of(from, to)));
                }
                WorldPoint turn = new WorldPoint(to.getX(), from.getY(), 0);
                return Optional.of(new RouteLeg(
                        null, null, from.distanceTo2D(turn) + turn.distanceTo2D(to), List.of(from, turn, to)));
            }
        });
        graph.setBoatSize(BoatSize.SKIFF);
        graph.setShortcuts(true);
        PortGraph background = graph.detachedSnapshot(start);
        RouteLeg route = background.routeFromPosition(start, Port.LUNAR_ISLE).orElseThrow();
        graph.mergeComputedRoutes(background);
        assertEquals(TravelStep.Kind.PORTAL, route.steps.get(1).kind);

        for (int moved : new int[] {4, 8, 12}) {
            RouteLeg remaining = graph.routeFromPosition(
                            new WorldPoint(start.getX(), start.getY() - moved, 0), Port.LUNAR_ISLE)
                    .orElseThrow();
            assertEquals(route.distance - moved, remaining.distance, 0);
            assertEquals(TravelStep.Kind.PORTAL, remaining.steps.get(1).kind);
            assertFalse(graph.hasMissingRoutes());
        }
        WorldPoint exit = shortcut.departure(BoatSize.SKIFF);
        RouteLeg after = graph.routeFromPosition(new WorldPoint(exit.getX() - 4, exit.getY(), 0), Port.LUNAR_ISLE)
                .orElseThrow();
        assertTrue(after.sailingOnly());
        assertEquals(3, searches[0]);

        graph.setShortcuts(false);
        assertTrue(graph.detachedSnapshot(null)
                .route(Port.PRIFDDINAS, Port.LUNAR_ISLE)
                .orElseThrow()
                .sailingOnly());
    }

    @Test
    public void returnJourneyReusesTheComputedRouteInReverse() {
        List<BoatSize> searches = new ArrayList<>();
        WorldPoint bend = new WorldPoint(3000, 3120, 0);
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
                    searches.add(boatSize);
                    return Optional.of(new RouteLeg(null, null, 120, List.of(from, bend, to)));
                })
                .detachedSnapshot(null);

        RouteLeg outward = graph.route(Port.PORT_SARIM, Port.MUSA_POINT).orElseThrow(AssertionError::new);
        RouteLeg returning = graph.route(Port.MUSA_POINT, Port.PORT_SARIM).orElseThrow(AssertionError::new);

        assertEquals(List.of(BoatSize.SLOOP), searches);
        assertEquals(Port.MUSA_POINT, returning.from);
        assertEquals(Port.PORT_SARIM, returning.to);
        assertEquals(
                List.of(Port.MUSA_POINT.navigationLocation, bend, Port.PORT_SARIM.navigationLocation),
                returning.points);
        assertEquals(outward.distance, returning.distance, 0);
        assertEquals(Port.PORT_SARIM.navigationLocation, outward.points.get(0));
    }

    @Test
    public void generatedPortRoutesServeEveryPairForEachBoatSize() {
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            throw new AssertionError("Precomputed port routes must not search terrain");
        });
        for (BoatSize boatSize : BoatSize.values()) {
            SailingRouteCache.load(graph, boatSize);
        }

        for (BoatSize boatSize : BoatSize.values()) {
            graph.setBoatSize(boatSize);
            for (Port from : Port.values()) {
                for (Port to : Port.values()) {
                    if (from == to) {
                        continue;
                    }
                    Optional<RouteLeg> outward = graph.route(from, to);
                    Optional<RouteLeg> returning = graph.route(to, from);
                    assertEquals(outward.isPresent(), returning.isPresent());
                    if (!outward.isPresent()) {
                        continue;
                    }
                    List<WorldPoint> reversedPoints = new ArrayList<>(outward.get().points);
                    Collections.reverse(reversedPoints);
                    assertEquals(reversedPoints, returning.get().points);
                    assertEquals(outward.get().distance, returning.get().distance, 0);
                    for (int index = 1; index < outward.get().points.size(); index++) {
                        WorldPoint previous = outward.get().points.get(index - 1);
                        WorldPoint next = outward.get().points.get(index);
                        int horizontal = next.getX() - previous.getX();
                        int vertical = next.getY() - previous.getY();
                        assertTrue(horizontal == 0 || vertical == 0 || Math.abs(horizontal) == Math.abs(vertical));
                    }
                }
            }
            assertFalse(graph.hasMissingRoutes());
        }
    }

    @Test
    public void changingBoatSizeRecalculatesCachedRoutes() {
        List<BoatSize> routedSizes = new ArrayList<>();
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
                    routedSizes.add(boatSize);
                    return Optional.of(new RouteLeg(null, null, 1, List.of(from, to)));
                })
                .detachedSnapshot(null);

        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);
        assertEquals(List.of(BoatSize.SLOOP), routedSizes);

        assertTrue(graph.setBoatSize(BoatSize.RAFT));
        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);
        assertEquals(List.of(BoatSize.SLOOP, BoatSize.RAFT), routedSizes);
    }

    @Test
    public void smallSteeringDeviationsKeepTheCurrentRouteUntilTheBoatLeavesIt() {
        int[] searches = {0};
        WorldPoint end = Port.MUSA_POINT.navigationLocation;
        WorldPoint corner = new WorldPoint(end.getX(), end.getY() - 40, 0);
        WorldPoint start = new WorldPoint(corner.getX() - 60, corner.getY(), 0);
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            searches[0]++;
            WorldPoint turn = new WorldPoint(to.getX(), from.getY(), 0);
            return Optional.of(
                    new RouteLeg(null, null, from.distanceTo(turn) + turn.distanceTo(to), List.of(from, turn, to)));
        });
        PortGraph initial = graph.detachedSnapshot(start);
        initial.routeFromPosition(start, Port.MUSA_POINT);
        graph.mergeComputedRoutes(initial);
        graph.routeFromPosition(start, Port.MUSA_POINT).orElseThrow();

        int[] offsets = {1, -1, 2, -2, 1, 0};
        for (int tick = 0; tick < offsets.length; tick++) {
            int progress = (tick + 1) * 4;
            WorldPoint boat = new WorldPoint(start.getX() + progress, start.getY() + offsets[tick], 0);
            RouteLeg remaining = graph.routeFromPosition(boat, Port.MUSA_POINT).orElseThrow();

            assertEquals(List.of(new WorldPoint(boat.getX(), start.getY(), 0), corner, end), remaining.points);
            assertEquals(100 - progress, remaining.distance, 0);
            assertFalse(graph.hasMissingRoutes());
        }
        assertEquals(1, searches[0]);

        WorldPoint away = new WorldPoint(start.getX() + 28, start.getY() + 10, 0);
        assertFalse(graph.routeFromPosition(away, Port.MUSA_POINT).isPresent());
        assertTrue(graph.hasMissingRoutes());
        PortGraph rerouted = graph.detachedSnapshot(away);
        RouteLeg newRoute = rerouted.routeFromPosition(away, Port.MUSA_POINT).orElseThrow();
        WorldPoint movedWhilePlanning = new WorldPoint(away.getX() + 4, away.getY() + 1, 0);
        assertFalse(graph.routeFromPosition(movedWhilePlanning, Port.MUSA_POINT).isPresent());
        graph.mergeComputedRoutes(rerouted);

        RouteLeg remaining =
                graph.routeFromPosition(movedWhilePlanning, Port.MUSA_POINT).orElseThrow();
        assertEquals(2, searches[0]);
        assertEquals(
                List.of(new WorldPoint(movedWhilePlanning.getX(), away.getY(), 0), newRoute.points.get(1), end),
                remaining.points);
        assertFalse(graph.hasMissingRoutes());
    }

    @Test
    public void movingAlongABentRouteCountsTheSegmentAfterTheNextWaypoint() {
        WorldPoint end = Port.ALDARIN.navigationLocation;
        WorldPoint corner = new WorldPoint(end.getX(), end.getY() - 160, 0);
        WorldPoint start = new WorldPoint(corner.getX() - 170, corner.getY(), 0);
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
                    assertEquals(start, from);
                    return Optional.of(new RouteLeg(null, null, 330, List.of(from, corner, to)));
                })
                .detachedSnapshot(start);
        assertEquals(330, graph.routeFromPosition(start, Port.ALDARIN).orElseThrow().distance, 0);

        for (int moved = 3; moved <= 4; moved++) {
            WorldPoint position = new WorldPoint(start.getX() + moved, start.getY(), 0);
            RouteLeg remaining = graph.routeFromPosition(position, Port.ALDARIN).orElseThrow();

            assertEquals(List.of(position, corner, end), remaining.points);
            assertEquals(330 - moved, remaining.distance, 0);
        }
    }

    @Test
    public void sailingAlongACachedPortRouteDoesNotStartAnotherSearch() {
        int[] searches = {0};
        WorldPoint destination = Port.MUSA_POINT.navigationLocation;
        WorldPoint approach = new WorldPoint(destination.getX() - 10, destination.getY(), 0);
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
                    searches[0]++;
                    return Optional.of(new RouteLeg(null, null, 100, List.of(from, approach, to)));
                })
                .detachedSnapshot(null);
        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);

        WorldPoint position = new WorldPoint(destination.getX() - 5, destination.getY(), 0);
        RouteLeg remaining = graph.routeFromPosition(position, Port.MUSA_POINT).orElseThrow(AssertionError::new);

        assertEquals(1, searches[0]);
        assertEquals(List.of(position, destination), remaining.points);
        assertEquals(5, remaining.distance, 0);
    }

    @Test
    public void liveRoutesLeaveUncachedSearchesToTheBackgroundPlanner() {
        int[] searches = {0};
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            searches[0]++;
            return Optional.of(new RouteLeg(null, null, from.distanceTo(to), List.of(from, to)));
        });
        WorldPoint position = new WorldPoint(3000, 3100, 0);

        assertFalse(graph.route(Port.PORT_SARIM, Port.MUSA_POINT).isPresent());
        assertFalse(graph.routeFromPosition(position, Port.CATHERBY).isPresent());
        assertEquals(0, searches[0]);
        assertTrue(graph.hasMissingRoutes());

        PortGraph detached = graph.detachedSnapshot(position);
        detached.route(Port.PORT_SARIM, Port.MUSA_POINT);
        detached.routeFromPosition(position, Port.CATHERBY);
        graph.mergeComputedRoutes(detached);

        assertTrue(graph.route(Port.PORT_SARIM, Port.MUSA_POINT).isPresent());
        assertTrue(graph.routeFromPosition(position, Port.CATHERBY).isPresent());
        assertFalse(graph.hasMissingRoutes());

        PortGraph nextPlan = graph.detachedSnapshot(position);
        assertTrue(nextPlan.route(Port.PORT_SARIM, Port.MUSA_POINT).isPresent());
        assertTrue(nextPlan.routeFromPosition(position, Port.CATHERBY).isPresent());
        assertEquals(2, searches[0]);
    }
}
