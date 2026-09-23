package com.harbourmaster.model;

public final class OfferScore {
    public final CourierTask task;
    public final String unavailableReason;
    public final boolean routeAvailable;
    public final double marginalDistance;
    public final double score;
    public final boolean freeTravel;
    public final OfferBundle bundle;

    public OfferScore(
            CourierTask task,
            String unavailableReason,
            boolean routeAvailable,
            double marginalDistance,
            double score,
            boolean freeTravel) {
        this(task, unavailableReason, routeAvailable, marginalDistance, score, freeTravel, null);
    }

    private OfferScore(
            CourierTask task,
            String unavailableReason,
            boolean routeAvailable,
            double marginalDistance,
            double score,
            boolean freeTravel,
            OfferBundle bundle) {
        this.bundle = bundle;
        this.task = task;
        this.unavailableReason = unavailableReason;
        this.routeAvailable = routeAvailable;
        this.marginalDistance = marginalDistance;
        this.score = score;
        this.freeTravel = freeTravel;
    }

    public boolean eligible() {
        return unavailableReason.isEmpty();
    }

    public boolean scorable() {
        return eligible() && routeAvailable && task.experience >= 0;
    }

    public OfferScore withBundle(OfferBundle bundle) {
        return new OfferScore(task, unavailableReason, routeAvailable, marginalDistance, score, freeTravel, bundle);
    }
}
