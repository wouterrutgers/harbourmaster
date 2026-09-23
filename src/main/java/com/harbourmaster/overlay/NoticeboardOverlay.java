package com.harbourmaster.overlay;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.OfferBundle;
import com.harbourmaster.model.OfferScore;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
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
        if (!config.rankOffers()
                || !plugin.getSnapshot().boardOpen
                || !visible(InterfaceID.PortTaskBoard.CONTAINER)
                || visible(InterfaceID.Worldmap.CONTENT)
                || visible(InterfaceID.PortTaskInfo.WINDOW)) {
            return null;
        }
        Graphics2D drawing = (Graphics2D) graphics.create();
        drawing.setColor(config.bestOfferColor());
        drawing.setStroke(new BasicStroke(3));
        Point mouse = client.getMouseCanvasPosition();
        for (OfferScore offer : plugin.getSnapshot().offers) {
            Widget widget = plugin.getNoticeboard().getWidgets().get(offer.task.databaseRow);
            if (widget == null || widget.isHidden()) {
                continue;
            }
            Rectangle bounds = widget.getBounds();
            if (offer.bundle != null) {
                drawing.draw(bounds);
            }
            if (bounds.contains(mouse.getX(), mouse.getY())) {
                tooltips.add(new Tooltip(details(offer)));
            }
        }
        drawing.dispose();
        return null;
    }

    private boolean visible(int component) {
        Widget widget = client.getWidget(component);
        return widget != null && !widget.isHidden();
    }

    private String details(OfferScore offer) {
        StringBuilder text = new StringBuilder(offer.task.name);
        text.append("<br>").append(offer.task.pickup.name).append(" to ").append(offer.task.delivery.name);
        text.append(
                offer.task.experience < 0
                        ? "<br>XP unavailable"
                        : String.format(Locale.ENGLISH, "<br>%,d xp (base)", offer.task.experience));
        if (!offer.eligible()) {
            text.append("<br>").append(offer.unavailableReason);
        } else if (!offer.routeAvailable) {
            text.append("<br>Route unavailable");
        } else {
            text.append(String.format(Locale.ENGLISH, "<br>+%,.0f sailing tiles", offer.marginalDistance));
            if (offer.task.experience >= 0) {
                text.append("<br>").append(efficiency(offer.score, offer.freeTravel));
            }
        }
        if (offer.bundle != null && offer.bundle.tasks.size() > 1) {
            OfferBundle bundle = offer.bundle;
            text.append("<br><br>Recommended combination");
            for (CourierTask task : bundle.tasks) {
                text.append("<br>- ").append(task.name);
            }
            text.append(String.format(
                    Locale.ENGLISH,
                    "<br>%,d xp combined (base)<br>+%,.0f sailing tiles combined<br>%s",
                    bundle.experience,
                    bundle.marginalDistance,
                    efficiency(bundle.score, bundle.freeTravel)));
        }
        return text.toString();
    }

    private String efficiency(double score, boolean freeTravel) {
        return freeTravel ? "No added sailing" : String.format(Locale.ENGLISH, "%.1f xp per added tile", score);
    }
}
