package com.harbourmaster.model;

import java.util.ArrayList;
import java.util.List;

public final class DockChecklist {
    public final Port port;
    public final List<RouteEvent> actions;

    private DockChecklist(Port port, List<RouteEvent> actions) {
        this.port = port;
        this.actions = List.copyOf(actions);
    }

    public static DockChecklist at(Port port, List<ActiveTask> tasks) {
        List<RouteEvent> actions = new ArrayList<>();
        if (port != null) {
            for (ActiveTask active : tasks) {
                if (active.definition == null || active.isFinished()) {
                    continue;
                }
                CourierTask task = active.definition;
                if (task.delivery == port && active.carried() > 0) {
                    actions.add(new RouteEvent(
                            active.slot,
                            task,
                            RouteEvent.Action.DELIVER,
                            port,
                            Math.min(active.carried(), active.deliveryRemaining())));
                }
                if (task.pickup == port && active.pickupRemaining() > 0) {
                    actions.add(new RouteEvent(
                            active.slot, task, RouteEvent.Action.PICKUP, port, active.pickupRemaining()));
                }
            }
        }
        actions.sort(java.util.Comparator.comparing(event -> event.action == RouteEvent.Action.PICKUP));
        return new DockChecklist(port, actions);
    }

    public boolean hasUnload() {
        return actions.stream().anyMatch(event -> event.action == RouteEvent.Action.DELIVER);
    }

    public DockChecklist follow(RoutePlan route) {
        if (!route.available || route.stops.isEmpty()) {
            return this;
        }
        return new DockChecklist(port, route.stops.get(0).port == port ? route.nextActions() : List.of());
    }

    public boolean hasAcceptance() {
        return actions.stream().anyMatch(event -> event.action == RouteEvent.Action.ACCEPT);
    }

    public boolean unloads(int itemId) {
        return actions.stream()
                .anyMatch(event -> event.action == RouteEvent.Action.DELIVER && event.task.itemId == itemId);
    }
}
