package com.harbourmaster.optimizer;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierPlan;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RoutePlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public final class CourierCyclePlanner {
    private static final int MAX_BUNDLE_CANDIDATES = 5;
    private static final int MAX_PLANNED_OFFERS = 5;
    private static final int MAX_ROUTE_EVENTS = 15;
    private final RouteOptimizer optimizer;
    private final CourierTimeModel timeModel = new CourierTimeModel();

    public CourierCyclePlanner(RouteOptimizer optimizer) {
        this.optimizer = optimizer;
    }

    public CourierPlan plan(
            Port start,
            WorldPoint boatPosition,
            List<ActiveTask> held,
            Map<Port, List<CourierTask>> observedOffers,
            int sailingLevel,
            int freeSlots,
            int tasksUntilReset) {
        CourierPlan best = evaluate(start, boatPosition, held, List.of(), freeSlots, tasksUntilReset);
        if (!best.available) {
            return best;
        }
        int incompleteTasks = 0;
        int heldEvents = 0;
        for (ActiveTask task : held) {
            if (task.isFinished()) {
                continue;
            }
            incompleteTasks++;
            heldEvents += (task.pickupRemaining() > 0 ? 1 : 0) + 1;
        }
        int maximumOffers = Math.min(MAX_PLANNED_OFFERS, freeSlots + incompleteTasks);
        maximumOffers = Math.min(maximumOffers, (MAX_ROUTE_EVENTS - heldEvents) / 3);
        if (maximumOffers == 0) {
            return best;
        }
        List<CourierPlan> singles = new ArrayList<>();
        for (CourierTask task : candidates(held, observedOffers, sailingLevel)) {
            CourierPlan plan = evaluate(start, boatPosition, held, List.of(task), freeSlots, tasksUntilReset);
            if (!plan.available) {
                continue;
            }
            singles.add(plan);
            if (better(plan, best)) {
                best = plan;
            }
        }
        if (maximumOffers == 1) {
            return best;
        }
        singles.sort(Comparator.comparingDouble((CourierPlan plan) -> plan.experiencePerHour)
                .reversed()
                .thenComparing(Comparator.comparingInt((CourierPlan plan) -> plan.courierExperience)
                        .reversed())
                .thenComparingInt(plan -> plan.selectedOffers.get(0).board.ordinal())
                .thenComparingInt(plan -> plan.selectedOffers.get(0).id));
        int candidateCount = Math.min(MAX_BUNDLE_CANDIDATES, singles.size());
        for (int mask = 1; mask < (1 << candidateCount); mask++) {
            int size = Integer.bitCount(mask);
            if (size < 2 || size > maximumOffers) {
                continue;
            }
            List<CourierTask> offers = new ArrayList<>();
            for (int index = 0; index < candidateCount; index++) {
                if ((mask & (1 << index)) != 0) {
                    offers.add(singles.get(index).selectedOffers.get(0));
                }
            }
            CourierPlan plan = evaluate(start, boatPosition, held, offers, freeSlots, tasksUntilReset);
            if (better(plan, best)) {
                best = plan;
            }
        }
        return best;
    }

    public CourierPlan relocate(CourierPlan plan, Port start, WorldPoint boatPosition) {
        return withRoute(plan, optimizer.relocate(plan.route, start, boatPosition));
    }

    public CourierPlan withRoute(CourierPlan plan, RoutePlan route) {
        return new CourierPlan(
                route,
                plan.selectedOffers,
                plan.courierExperience,
                timeModel.experiencePerHour(route, plan.courierExperience));
    }

    private CourierPlan evaluate(
            Port start,
            WorldPoint boatPosition,
            List<ActiveTask> held,
            List<CourierTask> offers,
            int freeSlots,
            int tasksUntilReset) {
        RoutePlan route = optimizer.optimizeWithOffers(start, boatPosition, held, offers, freeSlots, tasksUntilReset);
        if (!route.available) {
            return new CourierPlan(route, offers, 0, 0);
        }
        int experience = held.stream()
                        .filter(task -> !task.isFinished())
                        .mapToInt(task -> Math.max(0, task.definition.experience))
                        .sum()
                + offers.stream().mapToInt(task -> task.experience).sum();
        return new CourierPlan(route, offers, experience, timeModel.experiencePerHour(route, experience));
    }

    private static List<CourierTask> candidates(
            List<ActiveTask> held, Map<Port, List<CourierTask>> observedOffers, int sailingLevel) {
        Set<Integer> excludedIds = new HashSet<>();
        for (ActiveTask task : held) {
            excludedIds.add(task.taskId);
        }
        List<CourierTask> candidates = new ArrayList<>();
        for (List<CourierTask> offers : observedOffers.values()) {
            for (CourierTask task : offers) {
                if (task.experience < 0 || task.level > sailingLevel || !excludedIds.add(task.id)) {
                    continue;
                }
                candidates.add(task);
            }
        }
        return candidates;
    }

    private static boolean better(CourierPlan candidate, CourierPlan current) {
        if (!candidate.available) {
            return false;
        }
        return candidate.experiencePerHour > current.experiencePerHour
                || (candidate.experiencePerHour == current.experiencePerHour
                        && candidate.courierExperience > current.courierExperience);
    }
}
