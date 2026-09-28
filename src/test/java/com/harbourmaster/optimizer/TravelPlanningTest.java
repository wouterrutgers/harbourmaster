package com.harbourmaster.optimizer;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.data.BoatSize;
import com.harbourmaster.data.PortGraph;
import com.harbourmaster.data.SailingRouteCache;
import com.harbourmaster.model.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class TravelPlanningTest {
    @Test
    public void remainingBoardOffersStayRecommendedBeforePickingUpAcceptedTasks() {
        List<CourierTask> offers = List.of(courier(1, A, B, 1000), courier(2, A, C, 1000), courier(3, A, D, 1000));
        RouteOptimizer optimizer = new RouteOptimizer(line(), context(false, true, List.of(), List.of()));
        for (int acceptedCount = 1; acceptedCount <= offers.size(); acceptedCount++) {
            List<ActiveTask> held = offers.subList(0, acceptedCount).stream()
                    .map(task -> accepted(task))
                    .collect(Collectors.toList());
            List<CourierTask> remaining = offers.subList(acceptedCount, offers.size());
            RoutePlan route = optimizer.optimizeWithOffers(A, null, held, remaining, remaining.size(), 8);
            HarbourmasterSnapshot snapshot = new HarbourmasterSnapshot(
                    true, route, true, remaining.size(), DockChecklist.at(A, held), Map.of(), false);

            assertTrue(route.available);
            for (CourierTask offer : remaining) {
                assertTrue(snapshot.recommends(offer));
            }
            assertEquals(remaining.isEmpty() ? offers.size() : remaining.size(), snapshot.dock.actions.size());
            assertTrue(snapshot.dock.actions.stream()
                    .allMatch(event -> event.action
                            == (remaining.isEmpty() ? RouteEvent.Action.PICKUP : RouteEvent.Action.ACCEPT)));
        }
    }

    @Test
    public void noticeboardPlanningUsesGeneratedPortalPathsForEveryBoatSize() {
        PortGraph graph = new PortGraph((from, to, size) -> {
            throw new AssertionError("Noticeboard planning must use generated sailing routes");
        });
        for (BoatSize size : BoatSize.values()) {
            SailingRouteCache.load(graph, size);
        }
        graph.setShortcuts(true);
        CourierTask offer = new CourierTask(
                1, 1, "Northern delivery", 1, Port.PRIFDDINAS, 101, "Cargo", 3, 1000, Port.ALDARIN, Port.LUNAR_ISLE);
        for (BoatSize size : BoatSize.values()) {
            graph.setBoatSize(size);
            TravelContext travel = new TravelContext(
                    1, Port.PRIFDDINAS, size, false, false, true, List.of(teleport(Port.ALDARIN, 0)), List.of());
            CourierPlan plan = new CourierCyclePlanner(new RouteOptimizer(graph.detachedSnapshot(null), travel))
                    .plan(Port.PRIFDDINAS, null, List.of(), Map.of(Port.PRIFDDINAS, List.of(offer)), 99, 1, 8);
            assertTrue(plan.available);
            assertEquals(List.of(offer), plan.selectedOffers);
        }
    }

    @Test
    public void boardVisitRequiresAReturnToTheLoadedBoatBeforeDelivery() {
        CourierTask offer = new CourierTask(2, 2, "Return cargo", 1, B, 102, "Cargo", 3, 1000, D, A);
        List<ActiveTask> held = List.of(loaded(courier(1, C, A, 1000)));
        List<TravelMethod> methods = List.of(teleport(B, 0), teleport(A, 1));
        TravelContext travel = context(false, true, methods, List.of());
        RoutePlan plan = new RouteOptimizer(graph(), travel).optimizeWithOffers(A, null, held, List.of(offer), 1, 1);

        assertTrue(plan.available);
        assertEquals(
                List.of(B, A, D, A), plan.stops.stream().map(stop -> stop.port).collect(Collectors.toList()));
        assertEquals(TravelStep.Kind.TELEPORT, plan.legs.get(0).steps.get(0).kind);
        assertEquals(A.navigationLocation, plan.legs.get(1).steps.get(0).points.get(0));
        assertEquals(TravelStep.Kind.SAIL, plan.legs.get(2).steps.get(0).kind);

        RoutePlan withoutReturn = new RouteOptimizer(graph(), context(false, true, List.of(teleport(B, 0)), List.of()))
                .optimizeWithOffers(A, null, held, List.of(offer), 1, 1);
        assertTrue(withoutReturn.available);
        assertTrue(withoutReturn.legs.stream().allMatch(RouteLeg::sailingOnly));
    }

    @Test
    public void summonOnlyRepositionsEmptyBoat() {
        List<TravelMethod> methods = List.of(teleport(D, 0));
        TravelMethod summon = new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null);
        CourierTask task = courier(1, D, A, 1000);
        RoutePlan empty = new RouteOptimizer(graph(), context(false, true, methods, List.of(summon)))
                .optimize(A, List.of(accepted(task)));
        assertTrue(empty.available);
        assertEquals(
                List.of(TravelStep.Kind.TELEPORT, TravelStep.Kind.SUMMON),
                empty.legs.get(0).steps.stream().map(step -> step.kind).collect(Collectors.toList()));

        RoutePlan partialCargo = new RouteOptimizer(graph(), context(false, true, methods, List.of(summon)))
                .optimize(A, List.of(new ActiveTask(1, task.id, task, 1, 0)));
        assertTrue(partialCargo.legs.stream().allMatch(RouteLeg::sailingOnly));
    }

    @Test
    public void carriedCratesAndUnknownHoldContentsExcludeTeleportRepositioning() {
        List<TravelMethod> methods = List.of(teleport(D, 0));
        TravelMethod summon = new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null);
        List<ActiveTask> held = List.of(accepted(courier(1, D, A, 1000)));
        CourierTask offer = new CourierTask(2, 2, "Round trip", 1, A, 102, "Cargo", 3, 1000, D, A);
        RoutePlan carrying = new RouteOptimizer(graph(), context(true, true, methods, List.of(summon)))
                .optimizeWithOffers(A, null, List.of(), List.of(offer), 1, 1);
        RoutePlan unknownContents =
                new RouteOptimizer(graph(), context(false, false, methods, List.of(summon))).optimize(A, held);
        assertTrue(carrying.legs.stream().allMatch(RouteLeg::sailingOnly));
        assertTrue(unknownContents.legs.stream().allMatch(RouteLeg::sailingOnly));
    }

    @Test
    public void localAcceptanceIsShownBeforeSummoningTheBoatToThatSameDock() {
        CourierTask offer = new CourierTask(1, 1, "Local pickup", 1, B, 101, "Cargo", 3, 1000, B, C);
        TravelMethod summon = new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null);
        RouteOptimizer optimizer =
                new RouteOptimizer(graph(), context(false, true, List.of(teleport(A, 1)), List.of(summon)));
        RoutePlan route = optimizer.optimizeWithOffers(B, null, List.of(), List.of(offer), 1, 1);
        HarbourmasterSnapshot acceptance =
                new HarbourmasterSnapshot(true, route, false, 1, DockChecklist.at(B, List.of()), Map.of(), false);
        assertTrue(acceptance.dock.hasAcceptance());
        assertNull(acceptance.currentLeg);

        RoutePlan accepted = optimizer.optimize(B, List.of(accepted(offer)));
        HarbourmasterSnapshot reposition = new HarbourmasterSnapshot(
                true, accepted, false, 0, DockChecklist.at(B, List.of(accepted(offer))), Map.of(), false);
        assertEquals(TravelStep.Kind.SUMMON, reposition.currentLeg.steps.get(0).kind);
        assertTrue(reposition.dock.actions.isEmpty());
        assertFalse(reposition.sailingNext());
    }

    @Test
    public void travelTimeChangesTheRecommendedOfferBundle() {
        CourierTask ordinary = new CourierTask(1, 1, "Nearby", 1, A, 101, "Cargo", 3, 1000, A, B);
        CourierTask roundTrip = new CourierTask(2, 2, "Round trip", 1, A, 102, "Cargo", 3, 1800, D, A);
        Map<Port, List<CourierTask>> offers = Map.of(A, List.of(ordinary, roundTrip));
        TravelMethod summon = new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null);
        CourierPlan sailing =
                new CourierCyclePlanner(new RouteOptimizer(graph())).plan(A, null, List.of(), offers, 99, 1, 1);
        CourierPlan teleports = new CourierCyclePlanner(
                        new RouteOptimizer(graph(), context(false, true, List.of(teleport(D, 0)), List.of(summon))))
                .plan(A, null, List.of(), offers, 99, 1, 1);
        assertEquals(List.of(ordinary), sailing.selectedOffers);
        assertEquals(List.of(roundTrip), teleports.selectedOffers);
    }

    @Test
    public void deliveryAtThePlayersDockWaitsForTheLoadedBoatToReturn() {
        List<ActiveTask> tasks = List.of(loaded(courier(1, A, B, 1000)));
        RoutePlan route =
                new RouteOptimizer(line(), context(false, true, List.of(teleport(A, 1)), List.of())).optimize(B, tasks);
        HarbourmasterSnapshot snapshot =
                new HarbourmasterSnapshot(true, route, false, 1, DockChecklist.at(B, tasks), Map.of(), false);

        assertTrue(route.available);
        assertNotNull(snapshot.currentLeg);
        assertEquals(
                List.of(TravelStep.Kind.TELEPORT, TravelStep.Kind.SAIL),
                snapshot.currentLeg.steps.stream().map(step -> step.kind).collect(Collectors.toList()));
        assertTrue(snapshot.dock.actions.isEmpty());
        assertFalse(snapshot.sailingNext());
    }

    @Test
    public void offshoreStartUsesTheKnownBoatPositionWithoutAnAssociatedPort() {
        PortGraph graph = new PortGraph((from, to, size) -> {
            throw new AssertionError("Use the committed sailing route");
        });
        SailingRouteCache.load(graph, BoatSize.SKIFF);
        graph.setBoatSize(BoatSize.SKIFF);
        WorldPoint position = new WorldPoint(2328, 2683, 0);
        TravelContext travel = new TravelContext(1, null, BoatSize.SKIFF, true, false, true, List.of(), List.of());
        RoutePlan route = new RouteOptimizer(graph, travel)
                .optimize(null, position, List.of(loaded(courier(1, B, Port.DEEPFIN_POINT, 1000))), null);

        assertTrue(route.available);
        assertEquals(position, route.legs.get(0).steps.get(0).points.get(0));
        assertEquals(Port.DEEPFIN_POINT, route.stops.get(0).port);
    }

    private static TravelContext context(
            boolean carrying, boolean safe, List<TravelMethod> methods, List<TravelMethod> summons) {
        return new TravelContext(1, A, BoatSize.SLOOP, false, carrying, safe, methods, summons);
    }

    private static TravelMethod teleport(Port port, int boat) {
        return new TravelMethod(null, port, boat, TravelStep.Kind.TELEPORT, "Teleport", 1, null);
    }

    private static PortGraph graph() {
        return new PortGraph((from, to, size) -> Optional.of(new RouteLeg(null, null, 1000, List.of(from, to))))
                .detachedSnapshot(null);
    }
}
