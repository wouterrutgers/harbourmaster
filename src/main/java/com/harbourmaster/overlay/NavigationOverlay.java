package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.NavigationPath;
import com.harbourmaster.tracker.PortTracker;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public final class NavigationOverlay extends Overlay {
    private final Client client;
    private final HarbourmasterPlugin plugin;
    private final HarbourmasterConfig config;

    @Inject
    public NavigationOverlay(Client client, HarbourmasterPlugin plugin, HarbourmasterConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showWorldRoute()) {
            return null;
        }
        Graphics2D drawing = (Graphics2D) graphics.create();
        drawing.clipRect(
                client.getViewportXOffset(),
                client.getViewportYOffset(),
                client.getViewportWidth(),
                client.getViewportHeight());
        drawPaths(drawing, client, plugin.getSnapshot(), config, false);
        drawing.dispose();
        return null;
    }

    static void drawPaths(
            Graphics2D graphics,
            Client client,
            HarbourmasterSnapshot state,
            HarbourmasterConfig config,
            boolean minimap) {
        WorldPoint position = PortTracker.position(client);
        if (position == null) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int leg = state.navigation.size() - 1; leg >= 0; leg--) {
            NavigationPath path = state.navigation.get(leg);
            boolean current = path.leg == state.currentLeg;
            if (!current && !config.showCompleteRoute()) {
                continue;
            }
            graphics.setColor(current ? config.activeRouteColor() : config.futureRouteColor());
            graphics.setStroke(new BasicStroke(current ? 2.5f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Point previous = null;
            int segment = 0;
            for (NavigationPath.Sample point : path.points) {
                if (point.tile.distanceTo(position) > (minimap ? 32 : 100)) {
                    previous = null;
                    continue;
                }
                LocalPoint local = localPoint(client, point);
                Point canvas = local == null
                        ? null
                        : minimap
                                ? Perspective.localToMinimap(client, local)
                                : Perspective.localToCanvas(
                                        client,
                                        local,
                                        client.getTopLevelWorldView().getPlane());
                if (canvas != null && previous != null) {
                    drawSegment(graphics, previous, canvas, ++segment % 6 == 0);
                }
                previous = canvas;
            }
        }
    }

    static LocalPoint localPoint(Client client, NavigationPath.Sample sample) {
        LocalPoint tile = localPoint(client, sample.tile);
        if (tile == null) {
            return null;
        }
        double horizontal = sample.offsetX * Perspective.LOCAL_TILE_SIZE;
        double vertical = sample.offsetY * Perspective.LOCAL_TILE_SIZE;
        if (client.getTopLevelWorldView().isInstance()) {
            int template = client.getTopLevelWorldView()
                    .getInstanceTemplateChunks()[client.getTopLevelWorldView().getPlane()][tile.getSceneX() / 8][
                    tile.getSceneY() / 8];
            int rotation = (template >> 1) & 3;
            for (int turn = 0; turn < rotation; turn++) {
                double previousHorizontal = horizontal;
                horizontal = vertical;
                vertical = -previousHorizontal;
            }
        }
        return new LocalPoint(
                tile.getX() + (int) Math.round(horizontal),
                tile.getY() + (int) Math.round(vertical),
                tile.getWorldView());
    }

    static LocalPoint localPoint(Client client, WorldPoint point) {
        for (WorldPoint instance : WorldPoint.toLocalInstance(client.getTopLevelWorldView(), point)) {
            LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), instance);
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    static void drawSegment(Graphics2D graphics, Point from, Point to, boolean arrow) {
        graphics.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
        if (!arrow) {
            return;
        }
        double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
        int middleX = (from.getX() + to.getX()) / 2;
        int middleY = (from.getY() + to.getY()) / 2;
        for (double side : new double[] {angle - 0.55, angle + 0.55}) {
            graphics.drawLine(
                    middleX, middleY, middleX - (int) (7 * Math.cos(side)), middleY - (int) (7 * Math.sin(side)));
        }
    }
}
