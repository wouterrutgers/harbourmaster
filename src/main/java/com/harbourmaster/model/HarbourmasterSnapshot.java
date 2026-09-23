package com.harbourmaster.model;

import com.harbourmaster.tracker.CargoTracker;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class HarbourmasterSnapshot {
    public final boolean loggedIn;
    public final RoutePlan route;
    public final List<OfferScore> offers;
    public final boolean boardOpen;
    public final int freeSlots;
    public final DockChecklist dock;
    public final Map<Integer, CargoTracker.Destination> cargo;
    public final boolean depositCargo;
    public final List<NavigationPath> navigation;
    public final RouteLeg currentLeg;

    public HarbourmasterSnapshot(
            boolean loggedIn,
            RoutePlan route,
            List<OfferScore> offers,
            boolean boardOpen,
            int freeSlots,
            DockChecklist dock,
            Map<Integer, CargoTracker.Destination> cargo,
            boolean depositCargo) {
        this.loggedIn = loggedIn;
        this.route = route;
        this.offers = List.copyOf(offers);
        this.boardOpen = boardOpen;
        this.freeSlots = freeSlots;
        this.dock = dock;
        this.cargo = Map.copyOf(cargo);
        this.depositCargo = depositCargo;
        currentLeg = !route.legs.isEmpty() && route.legs.get(0).to == nextPort() && dock.port != nextPort()
                ? route.legs.get(0)
                : null;
        navigation = List.copyOf(route.legs.stream().map(NavigationPath::new).collect(Collectors.toList()));
    }

    public static HarbourmasterSnapshot empty() {
        return new HarbourmasterSnapshot(
                false, RoutePlan.empty(), List.of(), false, 0, DockChecklist.at(null, List.of()), Map.of(), false);
    }

    public Port nextPort() {
        return route.stops.isEmpty() ? null : route.stops.get(0).port;
    }
}
