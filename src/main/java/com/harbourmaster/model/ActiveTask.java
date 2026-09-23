package com.harbourmaster.model;

public final class ActiveTask {
    public final int slot;
    public final int taskId;
    public final CourierTask definition;
    public final int cargoTaken;
    public final int cargoDelivered;

    public ActiveTask(int slot, int taskId, CourierTask definition, int cargoTaken, int cargoDelivered) {
        this.slot = slot;
        this.taskId = taskId;
        this.definition = definition;
        this.cargoTaken = cargoTaken;
        this.cargoDelivered = cargoDelivered;
    }

    public int pickupRemaining() {
        return Math.max(0, definition.quantity - cargoTaken);
    }

    public int deliveryRemaining() {
        return Math.max(0, definition.quantity - cargoDelivered);
    }

    public int carried() {
        return Math.max(0, cargoTaken - cargoDelivered);
    }

    public boolean isFinished() {
        return definition != null && deliveryRemaining() == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ActiveTask)) {
            return false;
        }
        ActiveTask task = (ActiveTask) other;
        return slot == task.slot
                && taskId == task.taskId
                && definition == task.definition
                && cargoTaken == task.cargoTaken
                && cargoDelivered == task.cargoDelivered;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(slot, taskId, definition, cargoTaken, cargoDelivered);
    }
}
