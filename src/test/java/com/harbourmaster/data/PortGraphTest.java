package com.harbourmaster.data;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PortGraphTest {
    @Test
    public void shortestPathUsesIntermediatePortsAndReversesGeometryWithoutMutation() {
        PortGraph graph = new PortGraph(List.of(edge(A, B, 10), edge(B, C, 10), edge(A, C, 50)));
        RouteLeg forward = graph.route(A, C).get();
        RouteLeg reverse = graph.route(C, A).get();
        assertEquals(20, forward.distance, 0);
        assertEquals(List.of(C.navigationLocation, B.navigationLocation, A.navigationLocation), reverse.points);
        assertEquals(A.navigationLocation, forward.points.get(0));
    }

    @Test
    public void offPathBoatJoinsAheadWhilePreservingTheMappedCorner() {
        WorldPoint origin = new WorldPoint(100, 100, 0);
        WorldPoint corner = new WorldPoint(100, 200, 0);
        WorldPoint destination = new WorldPoint(200, 200, 0);
        PortGraph graph = new PortGraph(List.of(new RouteLeg(A, B, 200, List.of(origin, corner, destination))));
        WorldPoint boat = new WorldPoint(105, 100, 0);
        RouteLeg route = graph.routeFromPosition(boat, B).orElseThrow();
        assertEquals(boat, route.points.get(0));
        assertTrue(
                "Join ahead instead of sailing sideways and turning 90 degrees",
                route.points.get(1).getY() > 110);
        assertTrue(route.points.contains(corner));
        assertEquals(destination, route.points.get(route.points.size() - 1));
    }

    @Test
    public void adjacentVarlamorePathsDoNotSendTheBoatViaUnrelatedPorts() {
        PortGraph graph = new PortGraph(PortPathData.load());
        RouteLeg previous = null;
        for (int horizontal = 1890; horizontal <= 1940; horizontal += 5) {
            WorldPoint position = new WorldPoint(horizontal, 2940, 0);
            RouteLeg route =
                    graph.routeFromPosition(position, Port.CIVITAS_ILLA_FORTIS).orElseThrow();
            assertTrue("Unexpected detour at " + position, route.distance < 500);
            if (previous != null) {
                assertTrue(
                        "Five tiles of movement must not add a distant port detour",
                        Math.abs(previous.distance - route.distance) < 20);
            }
            previous = route;
        }
        double measured = 0;
        for (int index = 1; index < previous.points.size(); index++) {
            WorldPoint from = previous.points.get(index - 1);
            WorldPoint to = previous.points.get(index);
            measured += Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
        }
        assertEquals(measured, previous.distance, 0.00001);
    }

    @Test
    public void packagedNavigationDataProducesContinuousMeasuredRoutes() {
        PortGraph graph = new PortGraph(PortPathData.load());
        RouteLeg route = graph.route(A, Port.LUNAR_ISLE).get();
        double measured = 0;
        for (int index = 1; index < route.points.size(); index++) {
            measured += Math.hypot(
                    route.points.get(index).getX() - route.points.get(index - 1).getX(),
                    route.points.get(index).getY() - route.points.get(index - 1).getY());
        }
        assertEquals(measured, route.distance, 0.00001);
        assertEquals(A.navigationLocation, route.points.get(0));
        assertEquals(Port.LUNAR_ISLE.navigationLocation, route.points.get(route.points.size() - 1));
    }
}
