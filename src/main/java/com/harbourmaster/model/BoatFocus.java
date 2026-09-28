package com.harbourmaster.model;

public enum BoatFocus {
    NONE("None"),
    TELEPORT_FOCUS("Teleport focus"),
    GREATER_TELEPORT_FOCUS("Greater teleport focus");

    private final String label;

    BoatFocus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
