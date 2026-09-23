package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.DockChecklist;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.optimizer.RouteOptimizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class ActiveTaskTrackerTest {
    private final ActiveTaskTracker tracker = new ActiveTaskTracker();
    private final CourierTask courier = courier(1, A, D, 100);
    private final Map<Integer, Integer> varbits = new HashMap<>();
    private final Map<Integer, Integer> varplayers = new HashMap<>();
    private final Map<Integer, CourierTask> catalog = new HashMap<>(Map.of(1, courier));
    private Port port = A;

    @Test
    public void allFiveSlotsAreReadFreshAndLoginReplayCannotDuplicateThem() {
        assertTrue(read().isEmpty());
        for (int slot = 0; slot < 5; slot++) {
            varbits.put(TaskVarbits.IDS[slot], slot + 1);
            catalog.put(slot + 1, courier(slot + 1, A, D, 100));
        }
        List<ActiveTask> first = read();
        assertEquals(5, first.size());
        assertEquals(first, read());
        assertEquals(4, first.get(4).slot);
        varbits.put(TaskVarbits.IDS[1], 0);
        assertEquals(4, read().size());
    }

    @Test
    public void deliveredTaskOccupiesSlotUntilCleared() {
        progress(3, 3);
        assertEquals(1, tracker.occupiedSlots(id -> varbits.getOrDefault(id, 0)));
        varbits.put(TaskVarbits.IDS[0], 0);
        assertEquals(0, tracker.occupiedSlots(id -> varbits.getOrDefault(id, 0)));
    }

    @Test
    public void crewPickupUpdatesChecklistAndRouteBeforeDepositWithoutCountingTwice() {
        List<ActiveTask> beforePickup = List.of(progress(1, 0));
        assertEquals(2, DockChecklist.at(A, beforePickup).actions.get(0).quantity);

        varplayers.put(VarPlayerID.SAILING_CREW_HELD_CARGO_0, courier.itemId);
        varbits.put(VarbitID.SAILING_CREW_HELD_CARGO_0_AMOUNT, 1);
        assertEquals(1, DockChecklist.at(A, read()).actions.get(0).quantity);
        assertNotEquals(beforePickup, read());

        varplayers.put(VarPlayerID.SAILING_CREW_HELD_CARGO_1, courier.itemId);
        varbits.put(VarbitID.SAILING_CREW_HELD_CARGO_1_AMOUNT, 1);
        List<ActiveTask> withCrew = read();
        assertTrue(DockChecklist.at(A, withCrew).actions.isEmpty());
        RoutePlan route = new RouteOptimizer(line()).optimize(A, withCrew);
        assertEquals(
                List.of(RouteEvent.Action.DELIVER),
                route.stops.stream()
                        .flatMap(stop -> stop.events.stream())
                        .map(event -> event.action)
                        .collect(Collectors.toList()));
        varbits.put(TaskVarbits.TAKEN[0], 2);
        varbits.put(VarbitID.SAILING_CREW_HELD_CARGO_0_AMOUNT, 0);
        assertEquals(withCrew, read());
        varbits.put(TaskVarbits.TAKEN[0], 3);
        varbits.put(VarbitID.SAILING_CREW_HELD_CARGO_1_AMOUNT, 0);
        assertEquals(withCrew, read());
    }

    @Test
    public void crewCargoUsesQuantityAndItemIdentity() {
        progress(0, 0);
        varplayers.put(VarPlayerID.SAILING_CREW_HELD_CARGO_4, courier.itemId);
        varbits.put(VarbitID.SAILING_CREW_HELD_CARGO_4_AMOUNT, 2);
        assertEquals(1, DockChecklist.at(A, read()).actions.get(0).quantity);

        varplayers.put(VarPlayerID.SAILING_CREW_HELD_CARGO_4, courier(2, A, B, 100).itemId);
        assertEquals(3, DockChecklist.at(A, read()).actions.get(0).quantity);
    }

    @Test
    public void withdrawingCargoForPartialDeliveryDoesNotPretendMoreWasPickedUp() {
        progress(1, 0);
        port = D;
        varplayers.put(VarPlayerID.SAILING_CREW_HELD_CARGO_0, courier.itemId);
        varbits.put(VarbitID.SAILING_CREW_HELD_CARGO_0_AMOUNT, 1);
        assertEquals(2, read().get(0).pickupRemaining());
        assertEquals(1, DockChecklist.at(D, read()).actions.get(0).quantity);
    }

    private ActiveTask progress(int taken, int delivered) {
        varbits.put(TaskVarbits.IDS[0], 1);
        varbits.put(TaskVarbits.TAKEN[0], taken);
        varbits.put(TaskVarbits.DELIVERED[0], delivered);
        return read().get(0);
    }

    private List<ActiveTask> read() {
        return tracker.read(
                id -> varbits.getOrDefault(id, 0),
                id -> varplayers.getOrDefault(id, 0),
                catalog::get,
                id -> false,
                port);
    }
}
