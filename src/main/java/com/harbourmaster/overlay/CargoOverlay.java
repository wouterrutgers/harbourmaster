package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.tracker.CargoTracker;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class CargoOverlay extends WidgetItemOverlay {
    private final HarbourmasterPlugin plugin;
    private final HarbourmasterConfig config;
    private final ItemManager items;
    private final TooltipManager tooltips;
    private final Client client;

    @Inject
    public CargoOverlay(
            HarbourmasterPlugin plugin,
            HarbourmasterConfig config,
            ItemManager items,
            TooltipManager tooltips,
            Client client) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.tooltips = tooltips;
        this.client = client;
        showOnInventory();
        showOnInterfaces(InterfaceID.SAILING_BOAT_CARGOHOLD, InterfaceID.SAILING_BOAT_CARGOHOLD_SIDE);
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item) {
        HarbourmasterSnapshot state = plugin.getSnapshot();
        CargoTracker.Destination destination = state.cargo.get(itemId);
        if (destination == null) {
            return;
        }
        Rectangle bounds = item.getCanvasBounds();
        boolean unload = state.dock.unloads(itemId) && config.highlightUnloadCrates();
        if (unload) {
            graphics.drawImage(
                    items.getItemOutline(itemId, item.getQuantity(), config.unloadColor()), bounds.x, bounds.y, null);
        }
        if (config.showCargoDestinations() || unload) {
            Graphics2D drawing = (Graphics2D) graphics.create();
            drawing.setFont(FontManager.getRunescapeSmallFont());
            String text = unload ? "Unload" : destination.port.name;
            while (text.length() > 1 && drawing.getFontMetrics().stringWidth(text) > bounds.width) {
                text = text.substring(0, text.length() - 1);
            }
            drawing.setColor(Color.BLACK);
            drawing.drawString(text, bounds.x + 1, bounds.y + bounds.height);
            drawing.setColor(unload ? config.unloadColor() : Color.WHITE);
            drawing.drawString(text, bounds.x, bounds.y + bounds.height - 1);
            drawing.dispose();
            Point mouse = client.getMouseCanvasPosition();
            if (bounds.contains(mouse.getX(), mouse.getY())) {
                tooltips.add(new Tooltip((unload ? "Unload here: " : "Deliver to: ") + destination.port.name));
            }
        }
    }
}
