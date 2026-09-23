package com.harbourmaster.model;

import java.util.List;

public final class RouteStop {
    public final Port port;
    public final List<RouteEvent> events;

    public RouteStop(Port port, List<RouteEvent> events) {
        this.port = port;
        this.events = List.copyOf(events);
    }
}
