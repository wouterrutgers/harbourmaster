package com.harbourmaster.model;

import com.harbourmaster.data.BoatSize;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TravelContext {
    public final int boat;
    public final Port boatPort;
    public final BoatSize boatSize;
    public final boolean aboard;
    public final boolean carryingCargo;
    public final boolean summonSafe;
    public final List<TravelMethod> methods;
    public final List<TravelMethod> summons;
    public final Map<Integer, Integer> supplies;

    public TravelContext(
            int boat,
            Port boatPort,
            BoatSize boatSize,
            boolean aboard,
            boolean carryingCargo,
            boolean summonSafe,
            List<TravelMethod> methods,
            List<TravelMethod> summons,
            Map<Integer, Integer> supplies) {
        this.boat = boat;
        this.boatPort = boatPort;
        this.boatSize = boatSize;
        this.aboard = aboard;
        this.carryingCargo = carryingCargo;
        this.summonSafe = summonSafe;
        this.methods = List.copyOf(methods);
        this.summons = List.copyOf(summons);
        this.supplies = Map.copyOf(supplies);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TravelContext)) {
            return false;
        }
        TravelContext context = (TravelContext) other;
        return boat == context.boat
                && boatPort == context.boatPort
                && boatSize == context.boatSize
                && aboard == context.aboard
                && carryingCargo == context.carryingCargo
                && summonSafe == context.summonSafe
                && methods.equals(context.methods)
                && summons.equals(context.summons)
                && supplies.equals(context.supplies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boat, boatPort, boatSize, aboard, carryingCargo, summonSafe, methods, summons, supplies);
    }
}
