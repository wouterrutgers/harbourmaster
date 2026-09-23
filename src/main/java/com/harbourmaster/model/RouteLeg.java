package com.harbourmaster.model;

import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class RouteLeg {
    public final Port from;

    public final Port to;
    public final double distance;
    public final List<WorldPoint> points;

    public RouteLeg(Port from, Port to, double distance, List<WorldPoint> points) {
        this.from = from;
        this.to = to;
        this.distance = distance;
        this.points = List.copyOf(points);
    }
}
