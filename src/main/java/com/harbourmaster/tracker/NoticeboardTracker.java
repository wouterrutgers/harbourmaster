package com.harbourmaster.tracker;

import com.harbourmaster.data.PortTaskCatalog;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public final class NoticeboardTracker {
    private List<CourierTask> lastOffers = List.of();
    private Map<Integer, Widget> widgets = Map.of();
    private Port lastPort;
    private boolean openingDetails;
    private boolean detailsOpen;
    private boolean open;

    public void scan(Client client, PortTaskCatalog catalog) {
        Widget details = client.getWidget(InterfaceID.PortTaskInfo.WINDOW);
        if (details != null && !details.isHidden()) {
            openingDetails = false;
            detailsOpen = true;
            return;
        }
        if (openingDetails) {
            return;
        }
        detailsOpen = false;
        Widget container = client.getWidget(InterfaceID.PortTaskBoard.CONTAINER);
        open = container != null && !container.isHidden();
        if (!open) {
            widgets = Map.of();
            return;
        }
        Map<Integer, Widget> entries = new LinkedHashMap<>();
        List<CourierTask> offers = new ArrayList<>();
        Widget[] children = container.getDynamicChildren();
        if (children == null) {
            widgets = Map.of();
            lastOffers = List.of();
            lastPort = null;
            return;
        }
        for (Widget child : children) {
            Integer row = databaseRow(child == null ? null : child.getOnOpListener());
            if (row == null || entries.containsKey(row) || catalog.isIgnoredRow(row)) {
                continue;
            }
            CourierTask task = catalog.byRow(row);
            entries.put(row, child);
            offers.add(task);
        }
        widgets = Map.copyOf(entries);
        lastOffers = List.copyOf(offers);
        lastPort = offers.isEmpty() ? null : offers.get(0).board;
    }

    public static Integer databaseRow(Object[] listener) {
        return listener != null && listener.length > 3 && listener[3] instanceof Integer ? (Integer) listener[3] : null;
    }

    public void beginOpeningDetails(Widget widget) {
        if (widget != null) {
            Integer row = databaseRow(widget.getOnOpListener());
            if (row != null && widgets.containsKey(row)) {
                openingDetails = true;
            }
        }
    }

    public void clear() {
        lastOffers = List.of();
        widgets = Map.of();
        lastPort = null;
        openingDetails = false;
        detailsOpen = false;
        open = false;
    }

    public List<CourierTask> getOffers() {
        return lastOffers;
    }

    public Map<Integer, Widget> getWidgets() {
        return widgets;
    }

    public Port getPort() {
        return lastPort;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isDetailsOpen() {
        return detailsOpen;
    }

    public boolean isOpeningDetails() {
        return openingDetails;
    }
}
