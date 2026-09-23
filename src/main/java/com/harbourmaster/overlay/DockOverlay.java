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
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InventoryID;
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
        if (client.getLocalPlayer() == null) {
            return null;
        }
        HarbourmasterSnapshot state = plugin.getSnapshot();
        WorldView playerWorld = client.getLocalPlayer().getWorldView();
        boolean fetchCargo =
                state.dock.hasUnload() && playerWorld.isTopLevel() && !state.depositCargo && !carryingDelivery(state);
        for (GameObject object : plugin.getPorts().getObjects()) {
            Port port = Port.fromObject(object.getId());
            if (config.highlightGangplank() && state.dock.port != null) {
                boolean gangplank = object.getId() == ObjectID.SAILING_GANGPLANK_PROXY
                        || port == state.dock.port && object.getId() == port.gangplankObject;
                boolean boarding = (state.depositCargo || fetchCargo)
                        && playerWorld.isTopLevel()
                        && gangplank
                        && object.getWorldView().isTopLevel();
                boolean leavingBoat = !state.depositCargo
                        && !state.dock.actions.isEmpty()
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
                                            ? state.depositCargo ? "Board to deposit cargo" : "Board to fetch crates"
                                            : "Gangplank to " + state.dock.port.name));
                }
            }
            if (port != null && object.getId() == port.noticeboardObject && config.highlightNoticeboards()) {
                if (state.freeSlots > 0 || config.subdueFullBoards()) {
                    draw(
                            graphics,
                            object,
                            state.freeSlots > 0 ? config.bestOfferColor() : Color.GRAY,
                            List.of(state.freeSlots + " task slots free"));
                }
            }
            if (!state.dock.actions.isEmpty()
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
                    && (state.depositCargo || !state.dock.actions.isEmpty())
                    && CargoHoldObjects.IDS.contains(object.getId())
                    && !object.getWorldView().isTopLevel()
                    && object.getWorldView() == playerWorld) {
                boolean load = state.dock.actions.stream().anyMatch(event -> event.action == RouteEvent.Action.PICKUP);
                boolean unload = !state.depositCargo && state.dock.hasUnload();
                draw(
                        graphics,
                        object,
                        unload ? config.unloadColor() : config.loadColor(),
                        List.of(
                                state.depositCargo
                                        ? "Deposit task cargo"
                                        : unload
                                                ? (load ? "Unload, then load cargo" : "Unload task cargo")
                                                : "Load task cargo"));
            }
        }
        return null;
    }

    private boolean carryingDelivery(HarbourmasterSnapshot state) {
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        Item weapon = equipment == null ? null : equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        CargoTracker.Destination destination = weapon == null ? null : state.cargo.get(weapon.getId());
        return destination != null && destination.unload;
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
