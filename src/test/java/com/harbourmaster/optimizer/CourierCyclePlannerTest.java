package com.harbourmaster.optimizer;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierPlan;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RouteLeg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class CourierCyclePlannerTest {
    private final RouteOptimizer optimizer = new RouteOptimizer(line());
    private final CourierCyclePlanner planner = new CourierCyclePlanner(optimizer);

    @Test
    public void overlappingObservedTasksCanBeatTheBestSingleTaskByCourierXpPerHour() {
        CourierTask outbound = courier(1, B, D, 300);
        CourierTask returnTask = courier(2, D, B, 300);

        CourierPlan plan = planner.plan(A, null, List.of(), Map.of(A, List.of(outbound, returnTask)), 99, 2, 8);
        CourierPlan single = planner.plan(A, null, List.of(), Map.of(A, List.of(outbound)), 99, 2, 8);

        assertEquals(Set.of(outbound, returnTask), Set.copyOf(plan.selectedOffers));
        assertEquals(600, plan.courierExperience);
        assertTrue(plan.experiencePerHour > single.experiencePerHour);
        assertInOrder(plan, outbound, RouteEvent.Action.ACCEPT, RouteEvent.Action.PICKUP, RouteEvent.Action.DELIVER);
        assertInOrder(plan, returnTask, RouteEvent.Action.ACCEPT, RouteEvent.Action.PICKUP, RouteEvent.Action.DELIVER);
    }

    @Test
    public void aKnownOfferCanUseOneSlotReleasedByAHeldDelivery() {
        CourierTask held = courier(1, A, B, 100);
        CourierTask observed = new CourierTask(2, 2, "Return task", 1, B, 102, "Cargo 2", 3, 1000, A, B);
        List<ActiveTask> tasks = List.of(loaded(held));

        CourierPlan plan = planner.plan(A, null, tasks, Map.of(B, List.of(observed)), 99, 0, 8);

        assertEquals(List.of(observed), plan.selectedOffers);
        List<RouteEvent> events =
                plan.route.stops.stream().flatMap(stop -> stop.events.stream()).collect(Collectors.toList());
        assertTrue(index(events, held, RouteEvent.Action.DELIVER) < index(events, observed, RouteEvent.Action.ACCEPT));
    }

    @Test
    public void fullSlotsWithoutAKnownHeldDeliveryDoNotAssumeAFutureSlot() {
        CourierTask observed = courier(1, B, D, 1000);

        CourierPlan plan = planner.plan(A, null, List.of(), Map.of(A, List.of(observed)), 99, 0, 8);

        assertTrue(plan.selectedOffers.isEmpty());
        assertEquals(0, plan.courierExperience);
    }

    @Test
    public void heldLevelGatedAndUnknownRewardTasksAreNotAddedAsOffers() {
        CourierTask held = courier(1, B, D, 100);
        CourierTask levelGated = new CourierTask(2, 2, "High level", 99, A, 102, "Cargo 2", 3, 10000, A, B);
        CourierTask unknownReward = courier(3, B, D, -1);
        CourierTask eligible = courier(4, B, D, 500);

        CourierPlan plan = planner.plan(
                A,
                null,
                List.of(accepted(held)),
                Map.of(A, List.of(held, levelGated, unknownReward, eligible)),
                50,
                2,
                8);

        assertEquals(List.of(eligible), plan.selectedOffers);
    }

    @Test
    public void sequentialTasksReuseOneSlotWithoutAcceptingBeforeDelivery() {
        CourierTask outward = courier(1, A, B, 500);
        CourierTask onward = new CourierTask(2, 2, "Onward", 1, B, 102, "Cargo", 3, 1000, B, D);
        CourierPlan plan = planner.plan(A, null, List.of(), Map.of(A, List.of(outward), B, List.of(onward)), 99, 1, 8);

        assertEquals(Set.of(outward, onward), Set.copyOf(plan.selectedOffers));
        int occupied = 0;
        for (RouteEvent event :
                plan.route.stops.stream().flatMap(stop -> stop.events.stream()).collect(Collectors.toList())) {
            if (event.action == RouteEvent.Action.ACCEPT) {
                occupied++;
            }
            if (event.action == RouteEvent.Action.DELIVER) {
                occupied--;
            }
            assertTrue(occupied >= 0 && occupied <= 1);
        }
        assertEquals(0, occupied);
    }

    @Test
    public void sharedCrossingCanWinEvenWhenBothOffersFallOutsideTheTopFiveSingles() {
        PortGraph graph = new PortGraph((from, to, size) -> Optional.of(new RouteLeg(
                        null,
                        null,
                        (from.equals(A.navigationLocation)
                                        ? 0
                                        : from.equals(Port.LUNAR_ISLE.navigationLocation) ? 160 : 100)
                                + (to.equals(A.navigationLocation)
                                        ? 0
                                        : to.equals(Port.LUNAR_ISLE.navigationLocation) ? 160 : 100),
                        List.of(from, to))))
                .detachedSnapshot(null);
        List<CourierTask> offers = new ArrayList<>();
        for (Port port : List.of(B, C, D, E, Port.PANDEMONIUM)) {
            offers.add(courier(offers.size() + 1, A, port, 100));
        }
        CourierTask first = courier(6, A, Port.LUNAR_ISLE, 140);
        CourierTask second = courier(7, A, Port.LUNAR_ISLE, 140);
        offers.add(first);
        offers.add(second);

        CourierPlan plan = new CourierCyclePlanner(new RouteOptimizer(graph))
                .plan(A, null, List.of(), Map.of(A, offers), 99, 2, 8);

        assertEquals(Set.of(first, second), Set.copyOf(plan.selectedOffers));
        assertEquals(
                Set.of(first, second),
                plan.route.nextActions().stream().map(event -> event.task).collect(Collectors.toSet()));
    }

    @Test
    public void lowRateKnownOfferDoesNotDisplaceAFasterHeldRoute() {
        CourierTask held = courier(1, B, D, 10000);
        CourierTask distantLowReward = new CourierTask(2, 2, "Distant delivery", 1, E, 102, "Cargo 2", 3, 1, C, E);

        CourierPlan plan =
                planner.plan(A, null, List.of(accepted(held)), Map.of(E, List.of(distantLowReward)), 99, 1, 8);

        assertTrue(plan.selectedOffers.isEmpty());
        assertEquals(10000, plan.courierExperience);
    }

    @Test
    public void offersToPortsWithoutBoardsAreExcludedWhileHeldCargoStillGetsDelivered() {
        CourierTask held = courier(1, A, Port.PISCATORIS, 1);
        CourierTask noBoard = courier(2, A, Port.PISCATORIS, 1000000);
        CourierTask boardDestination = courier(3, B, D, 1000);

        CourierPlan plan =
                planner.plan(A, null, List.of(loaded(held)), Map.of(A, List.of(noBoard, boardDestination)), 99, 1, 8);

        assertEquals(List.of(boardDestination), plan.selectedOffers);
        assertTrue(plan.route.stops.stream()
                .flatMap(stop -> stop.events.stream())
                .anyMatch(event -> event.task == held && event.action == RouteEvent.Action.DELIVER));
    }

    @Test
    public void rewardRateUsesFixedSailingBoardingAndCargoActionTimes() {
        CourierTask offer = courier(1, B, D, 100);
        CourierPlan plan = planner.plan(A, null, List.of(), Map.of(A, List.of(offer)), 99, 4, 8);

        assertEquals(List.of(offer), plan.selectedOffers);
        assertEquals(100 * 6000 / (30.0 / 4 + 2 * 2 + 1 + 1 + 1), plan.experiencePerHour, 0.00001);
    }

    private static void assertInOrder(
            CourierPlan plan,
            CourierTask task,
            RouteEvent.Action first,
            RouteEvent.Action second,
            RouteEvent.Action third) {
        List<RouteEvent> events =
                plan.route.stops.stream().flatMap(stop -> stop.events.stream()).collect(Collectors.toList());
        assertTrue(index(events, task, first) < index(events, task, second));
        assertTrue(index(events, task, second) < index(events, task, third));
    }

    private static int index(List<RouteEvent> events, CourierTask task, RouteEvent.Action action) {
        for (int index = 0; index < events.size(); index++) {
            RouteEvent event = events.get(index);
            if (event.task == task && event.action == action) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }
}
