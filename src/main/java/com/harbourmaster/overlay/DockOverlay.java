package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.data.CargoHoldObjects;
import com.harbourmaster.model.DockGuidance;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public final class DockOverlay extends Overlay {
    private final Client client;
    private final HarbourmasterPlugin plugin;
    private final HarbourmasterConfig config;

    @Inject
    public DockOverlay(Client client, HarbourmasterPlugin plugin, HarbourmasterConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.isGuidanceActive() || client.getLocalPlayer() == null) {
            return null;
        }
        HarbourmasterSnapshot state = plugin.getSnapshot();
        DockGuidance guidance = plugin.getDockGuidance();
        Color color = guidance.unload ? config.unloadColor() : config.loadColor();
        WorldView playerWorld = client.getLocalPlayer().getWorldView();
        for (GameObject object : plugin.getPorts().getObjects()) {
            Port port = Port.fromObject(object.getId());
            if (config.highlightGangplank()
                    && state.dock.port != null
                    && guidance.target == DockGuidance.Target.GANGPLANK) {
                boolean shoreGangplank = port == state.dock.port
                        && object.getId() == port.gangplankObject
                        && object.getWorldView().isTopLevel();
                boolean ownGangplank = object.getId() == ObjectID.SAILING_GANGPLANK_PROXY
                        && !playerWorld.isTopLevel()
                        && object.getWorldView() == playerWorld;
                if (shoreGangplank || ownGangplank) {
                    drawGangplank(graphics, object, color, List.of(guidance.instruction));
                }
            }
            if (port != null && object.getId() == port.noticeboardObject && config.highlightNoticeboards()) {
                if (guidance.target == DockGuidance.Target.NOTICEBOARD && port == state.dock.port
                        || state.freeSlots == 0 && config.subdueFullBoards()) {
                    drawNoticeboard(graphics, object, port, state);
                }
            }
            if (guidance.target == DockGuidance.Target.LEDGER
                    && port == state.dock.port
                    && object.getId() == port.ledgerObject
                    && config.highlightLedger()) {
                List<String> lines = new ArrayList<>();
                lines.add(port.name + " ledger");
                for (RouteEvent action : state.dock.actions) {
                    if ((action.action == RouteEvent.Action.DELIVER) == guidance.unload) {
                        lines.add(action.description());
                    }
                }
                draw(graphics, object, color, lines);
            }
            if (config.highlightCargoHold()
                    && guidance.target == DockGuidance.Target.CARGO_HOLD
                    && CargoHoldObjects.IDS.contains(object.getId())
                    && !object.getWorldView().isTopLevel()
                    && object.getWorldView() == playerWorld) {
                draw(graphics, object, color, List.of(guidance.instruction));
            }
        }
        return null;
    }

    private void drawNoticeboard(Graphics2D graphics, GameObject object, Port port, HarbourmasterSnapshot state) {
        String label = state.freeSlots + (state.freeSlots == 1 ? " task slot free" : " task slots free");
        Color color = state.freeSlots > 0 ? config.bestOfferColor() : Color.GRAY;
        if (state.freeSlots > 0 && config.enableOptimizer() && config.rankOffers()) {
            if (!plugin.hasReadNoticeboard(port)) {
                label = "Check the noticeboard";
            } else if (plugin.isCalculatingPlan()) {
                label = "Checking offers";
            } else if (state.courierPlan != null && state.courierPlan.available) {
                long recommended = state.route.nextActions().stream()
                        .filter(event -> event.port == port && event.action == RouteEvent.Action.ACCEPT)
                        .count();
                label = recommended > 0
                        ? "Accept " + recommended + (recommended == 1 ? " task" : " tasks")
                        : "No recommended tasks";
                color = recommended > 0 ? config.bestOfferColor() : Color.GRAY;
            }
        }
        draw(graphics, object, color, List.of(label));
    }

    private static void draw(Graphics2D graphics, GameObject object, Color color, List<String> lines) {
        draw(graphics, object, object.getConvexHull(), color, lines);
    }

    private static void drawGangplank(Graphics2D graphics, GameObject proxy, Color color, List<String> lines) {
        WorldView world = proxy.getWorldView();
        if (proxy.getPlane() != world.getPlane()) {
            return;
        }
        LocalPoint location = proxy.getLocalLocation();
        Tile tile = world.getScene().getTiles()[0][location.getSceneX()][location.getSceneY()];
        GroundObject plank = tile == null ? null : tile.getGroundObject();
        if (plank != null) {
            draw(graphics, plank, plank.getConvexHull(), color, lines);
        }
    }

    private static void draw(Graphics2D graphics, TileObject object, Shape hull, Color color, List<String> lines) {
        if (hull != null) {
            OverlayUtil.renderPolygon(
                    graphics,
                    hull,
                    color,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), 25),
                    new BasicStroke(2));
        }
        int offset = (lines.size() - 1) * 15;
        for (String line : lines) {
            Point point = object.getCanvasTextLocation(graphics, line, 30);
            if (point != null) {
                OverlayUtil.renderTextLocation(graphics, new Point(point.getX(), point.getY() - offset), line, color);
            }
            offset -= 15;
        }
    }
}
