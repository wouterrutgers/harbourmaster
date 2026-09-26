package com.harbourmaster.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PortGraphTest {
    @Test
    public void returnJourneyReusesTheComputedRouteInReverse() {
        List<BoatSize> searches = new ArrayList<>();
        WorldPoint bend = new WorldPoint(3000, 3120, 0);
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            searches.add(boatSize);
            return Optional.of(new RouteLeg(null, null, 120, List.of(from, bend, to)));
        });

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
            graph.loadPortRoutes(boatSize, SailingRouteCache.load(boatSize));
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
                }
            }
        }
    }

    @Test
    public void movingToReachableWaterRetriesAFailedBoatRoute() {
        WorldPoint blocked = new WorldPoint(3000, 3100, 0);
        WorldPoint reachable = new WorldPoint(3001, 3100, 0);
        PortGraph graph = new PortGraph((from, to, boatSize) -> from.equals(blocked)
                ? Optional.empty()
                : Optional.of(new RouteLeg(null, null, from.distanceTo(to), List.of(from, to))));

        assertFalse(graph.routeFromPosition(blocked, Port.MUSA_POINT).isPresent());
        RouteLeg route = graph.routeFromPosition(reachable, Port.MUSA_POINT).orElseThrow(AssertionError::new);

        assertEquals(reachable, route.points.get(0));
        assertEquals(Port.MUSA_POINT, route.to);
    }

    @Test
    public void changingBoatSizeRecalculatesCachedRoutes() {
        List<BoatSize> routedSizes = new ArrayList<>();
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            routedSizes.add(boatSize);
            return Optional.of(new RouteLeg(null, null, 1, List.of(from, to)));
        });

        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);
        assertEquals(List.of(BoatSize.SLOOP), routedSizes);

        assertTrue(graph.setBoatSize(BoatSize.RAFT));
        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);
        assertEquals(List.of(BoatSize.SLOOP, BoatSize.RAFT), routedSizes);
    }

    @Test
    public void movingAlongTheCurrentRouteReusesItsRemainingPath() {
        int[] routeSearches = {0};
        Port destination = Port.MUSA_POINT;
        WorldPoint end = destination.navigationLocation;
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            routeSearches[0]++;
            return Optional.of(new RouteLeg(null, null, from.distanceTo(to), List.of(from, to)));
        });
        WorldPoint start = new WorldPoint(end.getX() - 10, end.getY(), 0);
        WorldPoint current = new WorldPoint(end.getX() - 5, end.getY(), 0);

        RouteLeg original = graph.routeFromPosition(start, destination).orElseThrow(AssertionError::new);
        RouteLeg remaining = graph.routeFromPosition(current, destination).orElseThrow(AssertionError::new);

        assertEquals(1, routeSearches[0]);
        assertEquals(current, remaining.points.get(0));
        assertEquals(end, remaining.points.get(remaining.points.size() - 1));
        assertEquals(original.distance / 2, remaining.distance, 0.00001);
    }

    @Test
    public void sailingAlongACachedPortRouteDoesNotStartAnotherSearch() {
        int[] searches = {0};
        WorldPoint destination = Port.MUSA_POINT.navigationLocation;
        WorldPoint approach = new WorldPoint(destination.getX() - 10, destination.getY(), 0);
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            searches[0]++;
            return Optional.of(new RouteLeg(null, null, 100, List.of(from, approach, to)));
        });
        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);

        WorldPoint position = new WorldPoint(destination.getX() - 5, destination.getY(), 0);
        RouteLeg remaining = graph.routeFromPosition(position, Port.MUSA_POINT).orElseThrow(AssertionError::new);

        assertEquals(1, searches[0]);
        assertEquals(List.of(position, destination), remaining.points);
        assertEquals(5, remaining.distance, 0);
    }

    @Test
    public void detachedSnapshotCopiesAndReturnsRoutesIndependently() {
        int[] routeSearches = {0};
        PortGraph graph = new PortGraph((from, to, boatSize) -> {
            routeSearches[0]++;
            return Optional.of(new RouteLeg(null, null, from.distanceTo(to), List.of(from, to)));
        });
        WorldPoint position = Port.PORT_SARIM.navigationLocation;

        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);
        graph.routeFromPosition(position, Port.MUSA_POINT);
        PortGraph detached = graph.detachedSnapshot(position);
        int searchesBeforeDetachedLookups = routeSearches[0];

        assertTrue(detached.route(Port.PORT_SARIM, Port.MUSA_POINT).isPresent());
        assertTrue(detached.routeFromPosition(position, Port.MUSA_POINT).isPresent());
        assertEquals(searchesBeforeDetachedLookups, routeSearches[0]);

        assertTrue(detached.route(Port.MUSA_POINT, Port.CATHERBY).isPresent());
        assertEquals(searchesBeforeDetachedLookups + 1, routeSearches[0]);
        graph.mergeComputedRoutes(detached);
        assertTrue(graph.route(Port.MUSA_POINT, Port.CATHERBY).isPresent());
        assertEquals(searchesBeforeDetachedLookups + 1, routeSearches[0]);
    }

    @Test
    public void detachedSnapshotWorksWhenTheBoatPositionIsUnknown() {
        PortGraph graph = new PortGraph(
                (from, to, boatSize) -> Optional.of(new RouteLeg(null, null, from.distanceTo(to), List.of(from, to))));

        graph.route(Port.PORT_SARIM, Port.MUSA_POINT);

        assertTrue(graph.detachedSnapshot(null)
                .route(Port.PORT_SARIM, Port.MUSA_POINT)
                .isPresent());
    }

    @Test
    public void livePortRoutesContinueInSmallSearchSlices() {
        int[] advances = {0};
        SailingRouter router = new SailingRouter() {
            @Override
            public Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize boatSize) {
                throw new AssertionError("The live graph must not run a full route synchronously");
            }

            @Override
            public SailingSearch search(WorldPoint from, WorldPoint to, BoatSize boatSize) {
                return new SailingSearch() {
                    private Optional<RouteLeg> result;

                    @Override
                    public boolean advance(int maximumExpandedStates) {
                        advances[0]++;
                        if (advances[0] == 3) {
                            result = Optional.of(new RouteLeg(null, null, 1, List.of(from, to)));
                        }
                        return result != null;
                    }

                    @Override
                    public Optional<RouteLeg> result() {
                        return result;
                    }

                    @Override
                    public int expandedStates() {
                        return advances[0];
                    }
                };
            }
        };
        PortGraph graph = new PortGraph(router);

        assertFalse(graph.route(Port.PORT_SARIM, Port.MUSA_POINT).isPresent());
        assertTrue(graph.hasPendingRouteSearches());
        graph.advanceRouteSearches(100_000_000);

        assertTrue(graph.route(Port.PORT_SARIM, Port.MUSA_POINT).isPresent());
        assertFalse(graph.hasPendingRouteSearches());
        assertEquals(3, advances[0]);
    }
}
