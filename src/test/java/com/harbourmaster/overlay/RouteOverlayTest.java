package com.harbourmaster.overlay;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.model.DockChecklist;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.NavigationPath;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.model.RouteStop;
import com.harbourmaster.model.TravelStep;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class RouteOverlayTest {
    @Test
    public void navigationDoesNotSampleAcrossAPortalJump() {
        WorldPoint entry = new WorldPoint(8, 0, 0);
        WorldPoint exit = new WorldPoint(1000, 0, 0);
        RouteLeg leg = new RouteLeg(
                A,
                B,
                16,
                List.of(),
                List.of(
                        new TravelStep(TravelStep.Kind.SAIL, "Approach", 2, List.of(new WorldPoint(0, 0, 0), entry)),
                        new TravelStep(TravelStep.Kind.PORTAL, "Portal", 2, List.of(entry, exit)),
                        new TravelStep(TravelStep.Kind.SAIL, "Depart", 2, List.of(exit, new WorldPoint(1008, 0, 0)))));
        HarbourmasterSnapshot state = new HarbourmasterSnapshot(
                true,
                new RoutePlan(true, "", 16, List.of(leg), List.of(new RouteStop(B, List.of()))),
                false,
                0,
                DockChecklist.at(A, List.of()),
                Map.of(),
                false);

        assertEquals(2, state.navigation.size());
        List<NavigationPath.Sample> approach = state.navigation.get(0).points;
        assertEquals(entry, approach.get(approach.size() - 1).tile);
        assertEquals(exit, state.navigation.get(1).points.get(0).tile);
    }

    @Test
    public void routeGeometryPreservesStraightLinesInRotatedInstances() {
        int[][][] chunks = new int[4][13][13];
        chunks[0][0][0] = (320 << 14) | (400 << 3) | (1 << 1);
        WorldView world = ApiStub.of(WorldView.class, (method, arguments) -> {
            switch (method) {
                case "isInstance":
                    return true;
                case "getInstanceTemplateChunks":
                    return chunks;
                case "getBaseX":
                    return 10000;
                case "getBaseY":
                    return 10000;
                case "getPlane":
                    return 0;
                case "getSizeX":
                case "getSizeY":
                    return 104;
                case "getId":
                    return WorldView.TOPLEVEL;
                default:
                    throw new AssertionError(method);
            }
        });
        Client client = ApiStub.of(Client.class, (method, arguments) -> {
            if (method.equals("getTopLevelWorldView")) {
                return world;
            }
            throw new AssertionError(method);
        });
        NavigationPath diagonal = new NavigationPath(new RouteLeg(
                A, B, Math.hypot(5, 1), List.of(new WorldPoint(2561, 3201, 0), new WorldPoint(2566, 3202, 0))));
        LocalPoint start = NavigationOverlay.localPoint(client, diagonal.points.get(0));
        LocalPoint middle = NavigationOverlay.localPoint(client, diagonal.points.get(1));
        LocalPoint end = NavigationOverlay.localPoint(client, diagonal.points.get(2));
        assertEquals((start.getX() + end.getX()) / 2, middle.getX());
        assertEquals((start.getY() + end.getY()) / 2, middle.getY());
    }
}
