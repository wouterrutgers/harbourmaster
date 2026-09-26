package com.harbourmaster.overlay;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.model.NavigationPath;
import com.harbourmaster.model.RouteLeg;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class RouteOverlayTest {
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
