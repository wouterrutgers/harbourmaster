package com.harbourmaster.model;

import java.util.List;

public final class CourierPlan {
    public final boolean available;
    public final String reason;
    public final RoutePlan route;
    public final List<CourierTask> selectedOffers;
    public final int courierExperience;
    public final double experiencePerHour;

    public CourierPlan(
            RoutePlan route, List<CourierTask> selectedOffers, int courierExperience, double experiencePerHour) {
        this.available = route.available;
        this.reason = route.reason;
        this.route = route;
        this.selectedOffers = List.copyOf(selectedOffers);
        this.courierExperience = courierExperience;
        this.experiencePerHour = experiencePerHour;
    }
}
