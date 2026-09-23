package com.harbourmaster.model;

public final class CourierTask {
    public final Port pickup;
    public final Port delivery;
    public final int id;
    public final int databaseRow;
    public final String name;
    public final int level;
    public final Port board;
    public final int itemId;
    public final String itemName;
    public final int quantity;
    public final int experience;

    public CourierTask(
            int id,
            int databaseRow,
            String name,
            int level,
            Port board,
            int itemId,
            String itemName,
            int quantity,
            int experience,
            Port pickup,
            Port delivery) {
        this.pickup = pickup;
        this.delivery = delivery;
        this.id = id;
        this.databaseRow = databaseRow;
        this.name = name;
        this.level = level;
        this.board = board;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.experience = experience;
    }
}
