package com.harbourmaster.model;

import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class RouteLeg {
    public final Port from;

    public final Port to;
    public final double distance;
    public final List<WorldPoint> points;
    public final List<TravelStep> steps;
    private final double travelTicks;

    public RouteLeg(Port from, Port to, double distance, List<WorldPoint> points) {
        this(
                from,
                to,
                distance,
                points,
                List.of(new TravelStep(
                        TravelStep.Kind.SAIL,
                        to == null ? "Sail" : "Sail to " + to.name,
                        distance / 4 + (from == to ? 0 : 2),
                        points)));
    }

    public RouteLeg(Port from, Port to, double distance, List<WorldPoint> points, List<TravelStep> steps) {
        this.from = from;
        this.to = to;
        this.distance = distance;
        this.points = List.copyOf(points);
        this.steps = List.copyOf(steps);
        double ticks = 0;
        for (TravelStep step : steps) {
            ticks += step.ticks;
        }
        travelTicks = ticks;
    }

    public double travelTicks() {
        return travelTicks;
    }

    public boolean sailingOnly() {
        return steps.stream().allMatch(step -> step.kind == TravelStep.Kind.SAIL);
    }
}
