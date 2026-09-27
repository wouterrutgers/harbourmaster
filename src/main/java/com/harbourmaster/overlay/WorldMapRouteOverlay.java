package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.TravelStep;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

public final class WorldMapRouteOverlay extends Overlay {
    private final Client client;
    private final HarbourmasterPlugin plugin;
    private final HarbourmasterConfig config;
    private final WorldMapOverlay projection;

    @Inject
    public WorldMapRouteOverlay(
            Client client, HarbourmasterPlugin plugin, HarbourmasterConfig config, WorldMapOverlay projection) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.projection = projection;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.isGuidanceActive()) {
            return null;
        }
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (!config.showWorldMap()
                || map == null
                || map.isHidden()
                || client.getWorldMap().getWorldMapData() == null) {
            return null;
        }
        Graphics2D drawing = (Graphics2D) graphics.create();
        Area clip = new Area(map.getBounds());
        for (int component :
                new int[] {InterfaceID.Worldmap.OVERVIEW_CONTAINER, InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0}) {
            Widget obstruction = client.getWidget(component);
            if (obstruction != null && !obstruction.isHidden()) {
                clip.subtract(new Area(obstruction.getBounds()));
            }
        }
        drawing.clip(clip);
        HarbourmasterSnapshot state = plugin.getSnapshot();
        List<RouteLeg> legs = state.route.legs;
        for (int index = legs.size() - 1; index >= 0; index--) {
            boolean current = legs.get(index) == state.currentLeg;
            if (!current && !config.showCompleteRoute()) {
                continue;
            }
            drawing.setColor(current ? config.activeRouteColor() : config.futureRouteColor());
            drawing.setStroke(new BasicStroke(current ? 3 : 1.5f));
            for (TravelStep step : legs.get(index).steps) {
                Point previous = null;
                for (WorldPoint point : step.points) {
                    Point canvas = projection.mapWorldPointToGraphicsPoint(point);
                    if (canvas != null && previous != null && step.kind == TravelStep.Kind.SAIL) {
                        NavigationOverlay.drawSegment(
                                drawing,
                                previous,
                                canvas,
                                Math.hypot(canvas.getX() - previous.getX(), canvas.getY() - previous.getY()) > 35);
                    }
                    if (canvas != null && step.kind != TravelStep.Kind.SAIL) {
                        drawing.drawOval(canvas.getX() - 4, canvas.getY() - 4, 8, 8);
                    }
                    previous = canvas;
                }
                if (previous != null) {
                    drawing.fillOval(previous.getX() - 3, previous.getY() - 3, 6, 6);
                    drawing.drawString(
                            step.kind == TravelStep.Kind.SAIL && legs.get(index).sailingOnly()
                                    ? legs.get(index).to.name
                                    : step.instruction,
                            previous.getX() + 7,
                            previous.getY());
                }
            }
        }
        drawing.dispose();
        return null;
    }
}
