package com.harbourmaster.model;

import java.util.List;

public final class OfferBundle {
    public final List<CourierTask> tasks;
    public final int experience;
    public final double marginalDistance;
    public final double score;
    public final boolean freeTravel;

    public OfferBundle(List<CourierTask> tasks, double marginalDistance) {
        this.tasks = List.copyOf(tasks);
        experience = tasks.stream().mapToInt(task -> task.experience).sum();
        freeTravel = marginalDistance < 0.0000001;
        this.marginalDistance = freeTravel ? 0 : marginalDistance;
        score = freeTravel ? 0 : experience / marginalDistance;
    }
}
