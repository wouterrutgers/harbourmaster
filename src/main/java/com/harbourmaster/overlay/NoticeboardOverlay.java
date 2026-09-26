package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.CourierPlan;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.HarbourmasterSnapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class NoticeboardOverlay extends Overlay {
    private final Client client;
    private final HarbourmasterPlugin plugin;
    private final HarbourmasterConfig config;
    private final TooltipManager tooltips;

    @Inject
    public NoticeboardOverlay(
            Client client, HarbourmasterPlugin plugin, HarbourmasterConfig config, TooltipManager tooltips) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltips = tooltips;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        HarbourmasterSnapshot state = plugin.getSnapshot();
        boolean openingDetails = plugin.getNoticeboard().isOpeningDetails();
        boolean boardVisible = visible(InterfaceID.PortTaskBoard.CONTAINER);
        if (visible(InterfaceID.Worldmap.CONTENT)
                || visible(InterfaceID.PortTaskInfo.WINDOW)
                || !openingDetails && !boardVisible) {
            return null;
        }
        if (boardVisible && plugin.isCalculatingPlan()) {
            renderCalculating(graphics);
            return null;
        }
        if (!config.rankOffers() || !state.boardOpen) {
            return null;
        }
        Graphics2D drawing = (Graphics2D) graphics.create();
        drawing.setColor(config.bestOfferColor());
        drawing.setStroke(new BasicStroke(3));
        Point mouse = client.getMouseCanvasPosition();
        for (CourierTask task : plugin.getNoticeboard().getOffers()) {
            Widget widget = plugin.getNoticeboard().getWidgets().get(task.databaseRow);
            if (widget == null || !openingDetails && widget.isHidden()) {
                continue;
            }
            Rectangle bounds = widget.getBounds();
            if (selected(state.courierPlan, task)) {
                drawing.draw(bounds);
            }
            if (!openingDetails && bounds.contains(mouse.getX(), mouse.getY())) {
                tooltips.add(new Tooltip(details(task, state.courierPlan)));
            }
        }
        if (!openingDetails
                && plugin.getNoticeboard().getOffers().stream().noneMatch(task -> selected(state.courierPlan, task))) {
            String message = state.courierPlan == null || state.courierPlan.selectedOffers.isEmpty()
                    ? "No offer is selected for the current courier plan"
                    : "The recommended task is at another board";
            Rectangle bounds = client.getWidget(InterfaceID.PortTaskBoard.FRAME).getBounds();
            drawing.setFont(FontManager.getRunescapeSmallFont());
            OverlayUtil.renderTextLocation(
                    drawing,
                    new Point(
                            bounds.x + (bounds.width - drawing.getFontMetrics().stringWidth(message)) / 2,
                            bounds.y + bounds.height - 11),
                    message,
                    Color.WHITE);
        }
        drawing.dispose();
        return null;
    }

    private void renderCalculating(Graphics2D graphics) {
        Rectangle bounds = client.getWidget(InterfaceID.PortTaskBoard.FRAME).getBounds();
        Graphics2D drawing = (Graphics2D) graphics.create();
        drawing.setColor(new Color(0, 0, 0, 153));
        drawing.fill(bounds);
        drawing.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fontMetrics = drawing.getFontMetrics();
        int y = bounds.y + bounds.height / 2 - fontMetrics.getHeight() + fontMetrics.getAscent();
        drawing.setColor(Color.WHITE);
        for (String message : new String[] {"Calculating route...", "Choosing tasks for your route..."}) {
            drawing.drawString(message, bounds.x + (bounds.width - fontMetrics.stringWidth(message)) / 2, y);
            y += fontMetrics.getHeight();
        }
        drawing.dispose();
    }

    private boolean visible(int component) {
        Widget widget = client.getWidget(component);
        return widget != null && !widget.isHidden();
    }

    private boolean selected(CourierPlan plan, CourierTask task) {
        return plan != null && plan.selectedOffers.stream().anyMatch(offer -> offer.id == task.id);
    }

    private String details(CourierTask task, CourierPlan plan) {
        StringBuilder text = new StringBuilder(task.name);
        text.append("<br>").append(task.pickup.name).append(" to ").append(task.delivery.name);
        text.append(
                task.experience < 0
                        ? "<br>xp unavailable"
                        : String.format(Locale.ENGLISH, "<br>%,d courier xp (base)", task.experience));
        if (selected(plan, task)) {
            text.append("<br>Selected for the current route");
            text.append(String.format(Locale.ENGLISH, "<br>Plan: %,d courier xp", plan.courierExperience));
            if (!plan.selectedOffers.isEmpty()) {
                text.append("<br><br>Selected offers");
                for (CourierTask selected : plan.selectedOffers) {
                    text.append("<br>• ").append(selected.name);
                }
            }
        } else {
            text.append("<br>Not included in the current courier plan");
        }
        return text.toString();
    }
}
