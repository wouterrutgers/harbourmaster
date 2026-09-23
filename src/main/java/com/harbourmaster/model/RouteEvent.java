package com.harbourmaster.model;

public final class RouteEvent {
    public final int slot;
    public final CourierTask task;
    public final Action action;
    public final Port port;
    public final int quantity;

    public RouteEvent(int slot, CourierTask task, Action action, Port port, int quantity) {
        this.slot = slot;
        this.task = task;
        this.action = action;
        this.port = port;
        this.quantity = quantity;
    }

    public enum Action {
        PICKUP,
        DELIVER
    }

    public String description() {
        return (action == Action.PICKUP ? "Pick up " : "Deliver ") + quantity + " × " + task.itemName;
    }
}
