package com.harbourmaster.optimizer;

import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.model.RouteStop;

final class CourierTimeModel {
    private static final double TICKS_PER_HOUR = 6000;

    public double experiencePerHour(RoutePlan route, int experience) {
        if (!route.available) {
            return 0;
        }

        double ticks =
                route.legs.stream().mapToDouble(CourierTimeModel::travelTicks).sum();
        for (RouteStop stop : route.stops) {
            ticks += stop.events.size();
        }
        return ticks <= 0 ? 0 : experience * TICKS_PER_HOUR / ticks;
    }

    static double travelTicks(RouteLeg leg) {
        return leg.travelTicks();
    }
}
