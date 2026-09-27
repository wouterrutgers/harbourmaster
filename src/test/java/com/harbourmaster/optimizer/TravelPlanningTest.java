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
import org.junit.Test;

public class TravelPlanningTest {
    private static final int SUPPLY = 563;

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
                    1,
                    Port.PRIFDDINAS,
                    size,
                    false,
                    false,
                    true,
                    List.of(teleport(Port.ALDARIN, 0)),
                    List.of(),
                    Map.of(SUPPLY, 1));
            CourierPlan plan = new CourierCyclePlanner(new RouteOptimizer(graph.detachedSnapshot(null), travel))
                    .plan(Port.PRIFDDINAS, null, List.of(), Map.of(Port.PRIFDDINAS, List.of(offer)), 99, 1, 8);
            assertTrue(plan.available);
            assertEquals(List.of(offer), plan.selectedOffers);
        }
    }

    @Test
    public void boardVisitReturnsToLoadedBoatBeforeDeliveryAndReservesReturnSupplies() {
        CourierTask offer = new CourierTask(2, 2, "Return cargo", 1, B, 102, "Cargo", 3, 1000, D, A);
        List<ActiveTask> held = List.of(loaded(courier(1, C, A, 1000)));
        List<TravelMethod> methods = List.of(teleport(B, 0), teleport(A, 1));
        TravelContext travel = context(false, true, methods, List.of(), 2);
        RoutePlan plan = new RouteOptimizer(graph(), travel).optimizeWithOffers(A, null, held, List.of(offer), 1, 1);

        assertTrue(plan.available);
        assertEquals(
                List.of(B, A, D, A), plan.stops.stream().map(stop -> stop.port).collect(Collectors.toList()));
        assertEquals(TravelStep.Kind.TELEPORT, plan.legs.get(0).steps.get(0).kind);
        assertEquals(A.navigationLocation, plan.legs.get(1).steps.get(0).points.get(0));
        assertEquals(TravelStep.Kind.SAIL, plan.legs.get(2).steps.get(0).kind);

        RoutePlan insufficient = new RouteOptimizer(graph(), context(false, true, methods, List.of(), 1))
                .optimizeWithOffers(A, null, held, List.of(offer), 1, 1);
        assertTrue(insufficient.available);
        assertTrue(insufficient.legs.stream().allMatch(RouteLeg::sailingOnly));
    }

    @Test
    public void summonOnlyRepositionsEmptyBoat() {
        List<TravelMethod> methods = List.of(teleport(D, 0));
        TravelMethod summon =
                new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null, Map.of(SUPPLY, 1));
        CourierTask task = courier(1, D, A, 1000);
        RoutePlan empty = new RouteOptimizer(graph(), context(false, true, methods, List.of(summon), 2))
                .optimize(A, List.of(accepted(task)));
        assertTrue(empty.available);
        assertEquals(
                List.of(TravelStep.Kind.TELEPORT, TravelStep.Kind.SUMMON),
                empty.legs.get(0).steps.stream().map(step -> step.kind).collect(Collectors.toList()));

        RoutePlan partialCargo = new RouteOptimizer(graph(), context(false, true, methods, List.of(summon), 2))
                .optimize(A, List.of(new ActiveTask(1, task.id, task, 1, 0)));
        assertTrue(partialCargo.legs.stream().allMatch(RouteLeg::sailingOnly));
    }

    @Test
    public void carriedCratesAndUnknownHoldContentsExcludeTeleportRepositioning() {
        List<TravelMethod> methods = List.of(teleport(D, 0));
        TravelMethod summon = new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null, Map.of());
        List<ActiveTask> held = List.of(accepted(courier(1, D, A, 1000)));
        CourierTask offer = new CourierTask(2, 2, "Round trip", 1, A, 102, "Cargo", 3, 1000, D, A);
        RoutePlan carrying = new RouteOptimizer(graph(), context(true, true, methods, List.of(summon), 2))
                .optimizeWithOffers(A, null, List.of(), List.of(offer), 1, 1);
        RoutePlan unknownContents =
                new RouteOptimizer(graph(), context(false, false, methods, List.of(summon), 2)).optimize(A, held);
        assertTrue(carrying.legs.stream().allMatch(RouteLeg::sailingOnly));
        assertTrue(unknownContents.legs.stream().allMatch(RouteLeg::sailingOnly));
    }

    @Test
    public void localAcceptanceIsShownBeforeSummoningTheBoatToThatSameDock() {
        CourierTask offer = new CourierTask(1, 1, "Local pickup", 1, B, 101, "Cargo", 3, 1000, B, C);
        TravelMethod summon = new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null, Map.of());
        RouteOptimizer optimizer =
                new RouteOptimizer(graph(), context(false, true, List.of(teleport(A, 1)), List.of(summon), 1));
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
        TravelMethod summon = new TravelMethod(null, A, 1, TravelStep.Kind.SUMMON, "Summon", 1, null, Map.of());
        CourierPlan sailing =
                new CourierCyclePlanner(new RouteOptimizer(graph())).plan(A, null, List.of(), offers, 99, 1, 1);
        CourierPlan teleports = new CourierCyclePlanner(
                        new RouteOptimizer(graph(), context(false, true, List.of(teleport(D, 0)), List.of(summon), 1)))
                .plan(A, null, List.of(), offers, 99, 1, 1);
        assertEquals(List.of(ordinary), sailing.selectedOffers);
        assertEquals(List.of(roundTrip), teleports.selectedOffers);
    }

    private static TravelContext context(
            boolean carrying, boolean safe, List<TravelMethod> methods, List<TravelMethod> summons, int supplies) {
        return new TravelContext(
                1, A, BoatSize.SLOOP, false, carrying, safe, methods, summons, Map.of(SUPPLY, supplies));
    }

    private static TravelMethod teleport(Port port, int boat) {
        return new TravelMethod(null, port, boat, TravelStep.Kind.TELEPORT, "Teleport", 1, null, Map.of(SUPPLY, 1));
    }

    private static PortGraph graph() {
        return new PortGraph((from, to, size) -> Optional.of(new RouteLeg(null, null, 1000, List.of(from, to))))
                .detachedSnapshot(null);
    }
}
