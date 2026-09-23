package com.harbourmaster.model;

import java.util.List;

public final class RoutePlan {
    public final boolean available;
    public final String reason;
    public final double distance;
    public final List<RouteLeg> legs;
    public final List<RouteStop> stops;

    public RoutePlan(boolean available, String reason, double distance, List<RouteLeg> legs, List<RouteStop> stops) {
        this.available = available;
        this.reason = reason;
        this.distance = distance;
        this.legs = List.copyOf(legs);
        this.stops = List.copyOf(stops);
    }

    public static RoutePlan unavailable(String reason) {
        return new RoutePlan(false, reason, 0, List.of(), List.of());
    }

    public static RoutePlan empty() {
        return new RoutePlan(true, "", 0, List.of(), List.of());
    }
}
