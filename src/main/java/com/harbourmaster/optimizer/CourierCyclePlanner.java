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
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

public final class CourierCyclePlanner {
    private static final int BUNDLE_SEARCH_WIDTH = 8;
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
        return plan(start, boatPosition, held, observedOffers, sailingLevel, freeSlots, tasksUntilReset, List.of());
    }

    public CourierPlan plan(
            Port start,
            WorldPoint boatPosition,
            List<ActiveTask> held,
            Map<Port, List<CourierTask>> observedOffers,
            int sailingLevel,
            int freeSlots,
            int tasksUntilReset,
            List<CourierTask> retainedOffers) {
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
        int maximumOffers = (MAX_ROUTE_EVENTS - heldEvents) / 3;
        if (freeSlots + incompleteTasks == 0) {
            return best;
        }
        List<CourierTask> candidates = candidates(held, observedOffers, sailingLevel);
        List<CourierTask> remaining =
                retainedOffers.stream().filter(candidates::contains).collect(Collectors.toList());
        if (!remaining.isEmpty()) {
            CourierPlan continued = evaluate(start, boatPosition, held, remaining, freeSlots, tasksUntilReset);
            if (continued.available) {
                return continued;
            }
        }
        List<CourierPlan> frontier = new ArrayList<>();
        for (CourierTask task : candidates) {
            CourierPlan plan = evaluate(start, boatPosition, held, List.of(task), freeSlots, tasksUntilReset);
            if (!plan.available) {
                continue;
            }
            frontier.add(plan);
            if (better(plan, best)) {
                best = plan;
            }
        }
        Comparator<CourierPlan> ranking = Comparator.comparingDouble((CourierPlan plan) -> plan.experiencePerHour)
                .reversed()
                .thenComparing(Comparator.comparingInt((CourierPlan plan) -> plan.courierExperience)
                        .reversed());
        for (int size = 2; size <= maximumOffers; size++) {
            List<CourierPlan> expanded = new ArrayList<>();
            Set<Set<CourierTask>> visited = new HashSet<>();
            for (CourierPlan partial : frontier) {
                for (CourierTask task : candidates) {
                    if (partial.selectedOffers.contains(task)) {
                        continue;
                    }
                    List<CourierTask> offers = new ArrayList<>(partial.selectedOffers);
                    offers.add(task);
                    if (!visited.add(Set.copyOf(offers))) {
                        continue;
                    }
                    CourierPlan plan = evaluate(start, boatPosition, held, offers, freeSlots, tasksUntilReset);
                    if (!plan.available) {
                        continue;
                    }
                    expanded.add(plan);
                    if (better(plan, best)) {
                        best = plan;
                    }
                }
            }
            expanded.sort(ranking);
            frontier = expanded.subList(0, Math.min(BUNDLE_SEARCH_WIDTH, expanded.size()));
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
                if (task.delivery.noticeboardObject < 0
                        || task.experience < 0
                        || task.level > sailingLevel
                        || !excludedIds.add(task.id)) {
                    continue;
                }
                candidates.add(task);
            }
        }
        candidates.sort(Comparator.comparingInt((CourierTask task) -> task.board.ordinal())
                .thenComparingInt(task -> task.id));
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
