package com.harbourmaster.model;

import java.util.List;

public final class RouteStop {
    public final Port port;
    public final List<RouteEvent> events;
    public final RouteLeg arrival;

    public RouteStop(Port port, List<RouteEvent> events) {
        this(port, events, null);
    }

    public RouteStop(Port port, List<RouteEvent> events, RouteLeg arrival) {
        this.port = port;
        this.events = List.copyOf(events);
        this.arrival = arrival;
    }
}
