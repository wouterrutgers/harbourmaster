package com.harbourmaster.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

public final class TravelMethod {
    public final Port origin;
    public final Port destination;
    public final int boat;
    public final TravelStep.Kind kind;
    public final String instruction;
    public final double ticks;
    public final double walkingTicks;
    public final WorldPoint arrival;
    public final Map<Integer, Integer> cost;

    public TravelMethod(
            Port origin,
            Port destination,
            int boat,
            TravelStep.Kind kind,
            String instruction,
            double ticks,
            WorldPoint arrival,
            Map<Integer, Integer> cost) {
        this(
                origin,
                destination,
                boat,
                kind,
                instruction,
                ticks,
                arrival,
                arrival == null ? 0 : arrival.distanceTo2D(destination.navigationLocation) / 2.0 + 2,
                cost);
    }

    public TravelMethod(
            Port origin,
            Port destination,
            int boat,
            TravelStep.Kind kind,
            String instruction,
            double ticks,
            WorldPoint arrival,
            double walkingTicks,
            Map<Integer, Integer> cost) {
        this.origin = origin;
        this.destination = destination;
        this.boat = boat;
        this.kind = kind;
        this.instruction = instruction;
        this.ticks = ticks;
        this.walkingTicks = walkingTicks;
        this.arrival = arrival;
        this.cost = Map.copyOf(cost);
    }

    public RouteLeg leg(Port from, Port to) {
        TravelStep transport = new TravelStep(
                kind, instruction, ticks, arrival == null ? List.of(to.navigationLocation) : List.of(arrival));
        if (arrival == null || kind == TravelStep.Kind.SUMMON) {
            return new RouteLeg(from, to, 0, List.of(), List.of(transport));
        }
        // Dock walks are estimates, not collision checked walking paths.
        TravelStep walk = new TravelStep(
                TravelStep.Kind.WALK, "Go to " + to.name + " dock", walkingTicks, List.of(to.navigationLocation));
        return new RouteLeg(from, to, 0, List.of(), List.of(transport, walk));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TravelMethod)) {
            return false;
        }
        TravelMethod method = (TravelMethod) other;
        return origin == method.origin
                && destination == method.destination
                && boat == method.boat
                && kind == method.kind
                && ticks == method.ticks
                && walkingTicks == method.walkingTicks
                && instruction.equals(method.instruction)
                && Objects.equals(arrival, method.arrival)
                && cost.equals(method.cost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, destination, boat, kind, instruction, ticks, walkingTicks, arrival, cost);
    }
}
