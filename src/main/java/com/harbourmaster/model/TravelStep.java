package com.harbourmaster.model;

import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class TravelStep {
    public enum Kind {
        SAIL,
        TELEPORT,
        CHARTER,
        SUMMON,
        PORTAL,
        WALK
    }

    public final Kind kind;
    public final String instruction;
    public final double ticks;
    public final List<WorldPoint> points;

    public TravelStep(Kind kind, String instruction, double ticks, List<WorldPoint> points) {
        this.kind = kind;
        this.instruction = instruction;
        this.ticks = ticks;
        this.points = List.copyOf(points);
    }
}
