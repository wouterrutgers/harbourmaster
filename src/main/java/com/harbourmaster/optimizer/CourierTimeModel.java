package com.harbourmaster.optimizer;

import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.model.RouteStop;

final class CourierTimeModel {
    private static final double BEST_SAILING_TILES_PER_TICK = 4;
    private static final double BOARDING_TICKS = 1;
    private static final double UNBOARDING_TICKS = 1;
    private static final double ACCEPT_TICKS = 1;
    private static final double PICKUP_TICKS = 1;
    private static final double DELIVERY_TICKS = 1;
    private static final double TICKS_PER_HOUR = 6000;

    public double experiencePerHour(RoutePlan route, int experience) {
        if (!route.available) {
            return 0;
        }

        double ticks = route.distance / BEST_SAILING_TILES_PER_TICK;
        ticks += route.legs.size() * (BOARDING_TICKS + UNBOARDING_TICKS);
        for (RouteStop stop : route.stops) {
            for (RouteEvent event : stop.events) {
                if (event.action == RouteEvent.Action.ACCEPT) {
                    ticks += ACCEPT_TICKS;
                    continue;
                }
                if (event.action == RouteEvent.Action.PICKUP) {
                    ticks += PICKUP_TICKS;
                    continue;
                }
                ticks += DELIVERY_TICKS;
            }
        }
        return ticks <= 0 ? 0 : experience * TICKS_PER_HOUR / ticks;
    }
}
