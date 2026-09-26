package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.RouteEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public final class RouteStatusOverlay extends OverlayPanel {
    private final HarbourmasterPlugin plugin;
    private final HarbourmasterConfig config;

    @Inject
    public RouteStatusOverlay(HarbourmasterPlugin plugin, HarbourmasterConfig config) {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(245, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        HarbourmasterSnapshot state = plugin.getSnapshot();
        if (!state.loggedIn || state.boardOpen) {
            return null;
        }
        panelComponent.getChildren().clear();
        if (!state.dock.actions.isEmpty()
                && (state.nextPort() == null || state.dock.port == state.nextPort())
                && config.showDockChecklist()) {
            title(state.dock.port.name + " dock", config.activeRouteColor());
            for (RouteEvent action : state.dock.actions) {
                line(
                        action.description(),
                        action.action == RouteEvent.Action.DELIVER ? config.unloadColor() : config.loadColor());
            }
            line("Finish these actions before sailing", Color.WHITE);
        } else if (config.showRouteOverlay() && !state.route.stops.isEmpty()) {
            title(
                    state.currentLeg == null
                            ? state.nextPort().name
                            : String.format(
                                    Locale.ENGLISH,
                                    "%,.0f tiles to %s",
                                    state.currentLeg.distance,
                                    state.nextPort().name),
                    config.activeRouteColor());
            for (RouteEvent action : state.route.nextActions()) {
                line(action.description(), Color.WHITE);
            }
            if (state.currentLeg == null) {
                line("Complete this stop before sailing", Color.WHITE);
            }
        } else if (config.showRouteOverlay() && !state.route.available) {
            title("Route unavailable", config.activeRouteColor());
            line(state.route.reason, Color.LIGHT_GRAY);
        } else if (config.showRouteOverlay() && state.freeSlots > 0) {
            title("Check a noticeboard", config.activeRouteColor());
            line("Open the board to choose courier tasks", Color.WHITE);
        } else {
            return null;
        }
        return super.render(graphics);
    }

    private void title(String text, Color color) {
        panelComponent
                .getChildren()
                .add(TitleComponent.builder().text(text).color(color).build());
    }

    private void line(String text, Color color) {
        panelComponent
                .getChildren()
                .add(LineComponent.builder().left(text).leftColor(color).build());
    }
}
