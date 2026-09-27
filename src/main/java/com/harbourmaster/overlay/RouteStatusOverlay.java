package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.TravelStep;
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
        if (!plugin.isGuidanceActive() || !state.loggedIn || state.boardOpen) {
            return null;
        }
        panelComponent.getChildren().clear();
        if (config.showRouteOverlay() && state.currentLeg != null && !state.currentLeg.sailingOnly()) {
            int next = state.currentLeg.steps.get(0).kind == TravelStep.Kind.SAIL
                            && state.currentLeg.steps.get(0).points.size() == 1
                    ? 1
                    : 0;
            TravelStep step = state.currentLeg.steps.get(next);
            title("Travel to " + state.nextPort().name, config.activeRouteColor());
            if (state.dock.port == null
                    && (step.kind == TravelStep.Kind.SUMMON || step.kind == TravelStep.Kind.CHARTER)) {
                line("Go to " + state.currentLeg.from.name + " dock", Color.WHITE);
            }
            line(step.instruction, Color.WHITE);
            if (state.currentLeg.steps.size() > next + 1) {
                line(state.currentLeg.steps.get(next + 1).instruction, Color.WHITE);
            }
            estimatedTravelTime(state.currentLeg);
        } else if (plugin.shouldUnloadCargo() && config.showDockChecklist()) {
            title(state.dock.port.name + " dock", config.activeRouteColor());
            line("Unload task cargo", config.unloadColor());
            line("Take delivery crates before going ashore", Color.WHITE);
        } else if (plugin.shouldCheckNoticeboard() && config.showDockChecklist()) {
            title(state.dock.port.name + " dock", config.activeRouteColor());
            line("Check the noticeboard", config.loadColor());
            line("Read the offers before sailing", Color.WHITE);
        } else if (!state.dock.actions.isEmpty()
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
                    state.currentLeg == null ? state.nextPort().name : "Travel to " + state.nextPort().name,
                    config.activeRouteColor());
            for (RouteEvent action : state.route.nextActions()) {
                line(action.description(), Color.WHITE);
            }
            if (state.currentLeg == null) {
                line(
                        state.dock.port == state.nextPort()
                                ? "Complete this stop before sailing"
                                : "Go to " + state.nextPort().name + " dock",
                        Color.WHITE);
            } else {
                estimatedTravelTime(state.currentLeg);
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

    private void estimatedTravelTime(RouteLeg leg) {
        long seconds = (long) Math.ceil(leg.travelTicks() * 0.6);
        line(
                String.format(Locale.ENGLISH, "Estimated travel: %dm %02ds", seconds / 60, seconds % 60),
                Color.LIGHT_GRAY);
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
