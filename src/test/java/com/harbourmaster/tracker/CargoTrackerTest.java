package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.DockChecklist;
import com.harbourmaster.model.RouteEvent;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CargoTrackerTest {
    @Test
    public void onlyCratesBoundForThisDockAreMarkedForUnloading() {
        CourierTask rum = courier(1, A, B, 100);
        CourierTask spice = courier(2, A, D, 100);
        Map<Integer, CargoTracker.Destination> cargo =
                new CargoTracker().destinations(List.of(loaded(rum), loaded(spice)), B);
        assertTrue(cargo.get(rum.itemId).unload);
        assertFalse(cargo.get(spice.itemId).unload);
        assertFalse(new CargoTracker().destinations(List.of(loaded(rum)), null).get(rum.itemId).unload);
    }

    @Test
    public void dockChecklistGroupsDeliveriesBeforeLoadsAndCountsOnlyAvailableCargo() {
        CourierTask partial = courier(1, A, B, 100);
        DockChecklist checklist = DockChecklist.at(
                B,
                List.of(
                        new ActiveTask(0, 1, partial, 2, 1),
                        loaded(courier(2, D, B, 100)),
                        accepted(courier(3, B, C, 100)),
                        accepted(courier(4, A, B, 100))));
        assertEquals(3, checklist.actions.size());
        assertEquals(RouteEvent.Action.DELIVER, checklist.actions.get(0).action);
        assertEquals(1, checklist.actions.get(0).quantity);
        assertEquals(RouteEvent.Action.DELIVER, checklist.actions.get(1).action);
        assertEquals(RouteEvent.Action.PICKUP, checklist.actions.get(2).action);
    }
}
