package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public final class MinimapRouteOverlay extends Overlay {
    private final Client client;
    private final HarbourmasterPlugin plugin;
    private final HarbourmasterConfig config;

    @Inject
    public MinimapRouteOverlay(Client client, HarbourmasterPlugin plugin, HarbourmasterConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showMinimap()) {
            return null;
        }
        Widget minimap = client.getWidget(
                !client.isResized()
                        ? InterfaceID.Toplevel.MINIMAP
                        : client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
                                ? InterfaceID.ToplevelPreEoc.MINIMAP
                                : InterfaceID.ToplevelOsrsStretch.MINIMAP);
        if (minimap == null || minimap.isHidden()) {
            return null;
        }
        Rectangle bounds = minimap.getBounds();
        Graphics2D drawing = (Graphics2D) graphics.create();
        drawing.clip(new Ellipse2D.Double(bounds.x + 5, bounds.y + 5, bounds.width - 10, bounds.height - 10));
        NavigationOverlay.drawPaths(drawing, client, plugin.getSnapshot(), config, true);
        drawing.dispose();
        return null;
    }
}
