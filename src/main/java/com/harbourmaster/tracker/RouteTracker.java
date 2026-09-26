package com.harbourmaster.tracker;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.model.RouteStop;
import com.harbourmaster.optimizer.RouteOptimizer;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class RouteTracker {
    private final RouteOptimizer optimizer;
    private RouteStop pendingStop;
    private RoutePlan previousPlan;
    private List<ActiveTask> previousTasks = List.of();

    public RouteTracker(RouteOptimizer optimizer) {
        this.optimizer = optimizer;
    }

    public RoutePlan update(Port start, List<ActiveTask> tasks) {
        return update(start, null, tasks);
    }

    public RoutePlan update(Port start, WorldPoint boatPosition, List<ActiveTask> tasks) {
        if (previousPlan != null && previousPlan.available && tasks.equals(previousTasks)) {
            RoutePlan relocated = optimizer.relocate(previousPlan, start, boatPosition);
            if (relocated.available) {
                previousPlan = relocated;
            }
            return relocated;
        }
        if (tasks.stream().anyMatch(task -> previousTasks.stream()
                .noneMatch(previous -> previous.slot == task.slot && previous.taskId == task.taskId))) {
            pendingStop = null;
        }
        Port firstStop = pendingStop != null && pendingStop.events.stream().anyMatch(event -> pending(event, tasks))
                ? pendingStop.port
                : null;
        RoutePlan route = optimizer.optimize(start, boatPosition, tasks, firstStop);
        previousTasks = List.copyOf(tasks);
        if (route.available) {
            pendingStop = route.stops.isEmpty() ? null : route.stops.get(0);
        }
        previousPlan = route;
        return route;
    }

    private static boolean pending(RouteEvent event, List<ActiveTask> tasks) {
        for (ActiveTask task : tasks) {
            if (task.slot == event.slot && task.taskId == event.task.id) {
                return task.definition == null
                        || (event.action == RouteEvent.Action.PICKUP
                                        ? task.pickupRemaining()
                                        : task.deliveryRemaining())
                                > 0;
            }
        }
        return false;
    }

    public void clear() {
        pendingStop = null;
        previousPlan = null;
        previousTasks = List.of();
    }
}
