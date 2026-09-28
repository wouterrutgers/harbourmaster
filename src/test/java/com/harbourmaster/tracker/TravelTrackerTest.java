package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.*;
import com.harbourmaster.optimizer.RouteOptimizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class TravelTrackerTest {
    private final TravelTracker tracker = new TravelTracker();
    private boolean teleportsEnabled;
    private BoatFocus boat1Focus = BoatFocus.TELEPORT_FOCUS;
    private final HarbourmasterConfig config = new HarbourmasterConfig() {
        @Override
        public boolean aldarinTeleport() {
            return teleportsEnabled;
        }

        @Override
        public boolean sailorsAmuletDeepfinPoint() {
            return teleportsEnabled;
        }

        @Override
        public boolean summonBoat() {
            return teleportsEnabled;
        }

        @Override
        public boolean teleportToBoat() {
            return teleportsEnabled;
        }

        @Override
        public BoatFocus boat1Focus() {
            return boat1Focus;
        }

        @Override
        public BoatFocus boat2Focus() {
            return BoatFocus.GREATER_TELEPORT_FOCUS;
        }
    };
    private final Map<Integer, Integer> varbits = new HashMap<>();
    private final Map<Integer, Item[]> containers = new HashMap<>();
    private final Client client = ApiStub.of(Client.class, (method, arguments) -> {
        switch (method) {
            case "getVarbitValue":
                return varbits.getOrDefault(arguments[0], 0);
            case "getLocalPlayer":
                return null;
            case "getDBTableField":
                return new Object[] {
                    Port.fromDatabaseRow((Integer) arguments[0]).ordinal()
                };
            case "getItemContainer":
                Item[] items = containers.get(arguments[0]);
                return items == null
                        ? null
                        : ApiStub.of(ItemContainer.class, (operation, ignored) -> {
                            assertEquals("getItems", operation);
                            return items;
                        });
            case "getItemDefinition":
                return ApiStub.of(ItemComposition.class, (operation, ignored) -> {
                    assertEquals("getName", operation);
                    return "Staff of air";
                });
            default:
                throw new AssertionError(method);
        }
    });

    @Test
    public void manualSelectionsAllowTeleportsWithoutGearSuppliesOrUnlocks() {
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SPELLBOOK, 2);
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);
        List<ActiveTask> tasks = List.of(accepted(courier(1, Port.ALDARIN, A, 1000)));

        teleportsEnabled = true;
        assertTrue(usesSummon(plan(A, tasks)));

        teleportsEnabled = false;
        containers.put(InventoryID.INV, new Item[] {
            new Item(ItemID.NZONE_TELETAB_ALDARIN, 10), new Item(ItemID.POH_TABLET_TELEPORTBOATTOME, 10)
        });
        assertFalse(usesSummon(plan(A, tasks)));
    }

    @Test
    public void enabledAmuletStillRequiresKnownBoatContentsBeforeSummoning() {
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        teleportsEnabled = true;
        List<ActiveTask> tasks = List.of(accepted(courier(1, Port.DEEPFIN_POINT, A, 1000)));

        assertFalse(usesSummon(plan(A, tasks)));
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);
        assertTrue(usesSummon(plan(A, tasks)));
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[] {new Item(ItemID.STAFF_OF_AIR, 1)});
        assertFalse(usesSummon(plan(A, tasks)));
    }

    @Test
    public void summoningAndReturningRespectTheCourierBoatsOwnFocus() {
        teleportsEnabled = true;
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SAILING_BOAT_2_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_2_PORT, B.ordinal());
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);
        List<ActiveTask> pickup = List.of(accepted(courier(1, Port.ALDARIN, A, 1000)));

        boat1Focus = BoatFocus.NONE;
        assertFalse(usesSummon(plan(A, pickup)));
        boat1Focus = BoatFocus.TELEPORT_FOCUS;
        assertTrue(usesSummon(plan(A, pickup)));

        List<ActiveTask> delivery = List.of(loaded(courier(1, A, C, 1000)));
        assertFalse(plan(B, delivery).available);
        boat1Focus = BoatFocus.GREATER_TELEPORT_FOCUS;
        RoutePlan returning = plan(B, delivery);
        assertTrue(returning.available);
        assertEquals(TravelStep.Kind.TELEPORT, returning.legs.get(0).steps.get(0).kind);
        assertEquals(
                A.navigationLocation, returning.legs.get(0).steps.get(0).points.get(0));
    }

    private RoutePlan plan(Port start, List<ActiveTask> tasks) {
        PortGraph graph = new PortGraph(
                        (from, to, size) -> Optional.of(new RouteLeg(null, null, 1000, List.of(from, to))))
                .detachedSnapshot(null);
        return new RouteOptimizer(graph, tracker.read(client, tasks, config, null)).optimize(start, tasks);
    }

    private static boolean usesSummon(RoutePlan plan) {
        assertTrue(plan.available);
        return plan.legs.stream()
                .flatMap(leg -> leg.steps.stream())
                .anyMatch(step -> step.kind == TravelStep.Kind.SUMMON);
    }
}
