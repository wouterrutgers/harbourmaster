package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.data.CargoHoldObjects;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.tracker.CargoTracker;
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
    private final CargoTracker cargo = new CargoTracker();

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
        boolean checkNoticeboard = plugin.shouldCheckNoticeboard();
        boolean unloadCargo = plugin.shouldUnloadCargo();
        WorldView playerWorld = client.getLocalPlayer().getWorldView();
        boolean fetchCargo = state.dock.hasUnload()
                && playerWorld.isTopLevel()
                && !state.depositCargo
                && !cargo.carryingDelivery(client, state.cargo);
        for (GameObject object : plugin.getPorts().getObjects()) {
            Port port = Port.fromObject(object.getId());
            if (config.highlightGangplank() && state.dock.port != null) {
                boolean gangplank = object.getId() == ObjectID.SAILING_GANGPLANK_PROXY
                        || port == state.dock.port && object.getId() == port.gangplankObject;
                boolean boarding = (state.depositCargo || fetchCargo || state.currentLeg != null && !checkNoticeboard)
                        && playerWorld.isTopLevel()
                        && gangplank
                        && object.getWorldView().isTopLevel();
                boolean leavingBoat = !state.depositCargo
                        && (unloadCargo || !state.dock.actions.isEmpty() || checkNoticeboard)
                        && !playerWorld.isTopLevel()
                        && gangplank
                        && (object.getWorldView().isTopLevel() || object.getWorldView() == playerWorld);
                if (boarding || leavingBoat) {
                    drawGangplank(
                            graphics,
                            object,
                            state.depositCargo || !state.dock.hasUnload() ? config.loadColor() : config.unloadColor(),
                            List.of(
                                    boarding
                                            ? state.depositCargo
                                                    ? "Board to deposit cargo"
                                                    : fetchCargo ? "Board to fetch crates" : "Board to sail"
                                            : "Gangplank to " + state.dock.port.name));
                }
            }
            if (port != null && object.getId() == port.noticeboardObject && config.highlightNoticeboards()) {
                if (state.freeSlots > 0 || config.subdueFullBoards()) {
                    drawNoticeboard(graphics, object, port, state, unloadCargo);
                }
            }
            if (!state.dock.actions.isEmpty()
                    && !unloadCargo
                    && !state.dock.hasAcceptance()
                    && port == state.dock.port
                    && object.getId() == port.ledgerObject
                    && config.highlightLedger()) {
                List<String> lines = new ArrayList<>();
                lines.add(port.name + " ledger");
                for (RouteEvent action : state.dock.actions) {
                    lines.add(action.description());
                }
                draw(graphics, object, state.dock.hasUnload() ? config.unloadColor() : config.loadColor(), lines);
            }
            if (config.highlightCargoHold()
                    && (state.depositCargo
                            || unloadCargo
                            || !state.dock.actions.isEmpty() && !state.dock.hasAcceptance() && !state.dock.hasUnload())
                    && CargoHoldObjects.IDS.contains(object.getId())
                    && !object.getWorldView().isTopLevel()
                    && object.getWorldView() == playerWorld) {
                boolean load = state.dock.actions.stream().anyMatch(event -> event.action == RouteEvent.Action.PICKUP);
                draw(
                        graphics,
                        object,
                        unloadCargo ? config.unloadColor() : config.loadColor(),
                        List.of(
                                state.depositCargo
                                        ? "Deposit task cargo"
                                        : unloadCargo
                                                ? (load ? "Unload, then load cargo" : "Unload task cargo")
                                                : "Load task cargo"));
            }
        }
        return null;
    }

    private void drawNoticeboard(
            Graphics2D graphics, GameObject object, Port port, HarbourmasterSnapshot state, boolean unloadCargo) {
        String label = state.freeSlots + (state.freeSlots == 1 ? " task slot free" : " task slots free");
        Color color = state.freeSlots > 0 ? config.bestOfferColor() : Color.GRAY;
        if (state.freeSlots > 0 && unloadCargo && port == state.dock.port) {
            label = "Unload cargo first";
            color = Color.GRAY;
        } else if (state.freeSlots > 0 && config.enableOptimizer() && config.rankOffers()) {
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
