package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.data.BoatSize;
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
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class TravelTrackerTest {
    private final TravelTracker tracker = new TravelTracker();
    private boolean teleportsEnabled;
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
    public void damageProtectionFollowsTheCourierBoatWhileAnotherBoatWasLastBoarded() {
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 2);
        varbits.put(VarbitID.SAILING_BOAT_1_KEEL, 5);
        Client boats = ApiStub.of(Client.class, (method, arguments) -> {
            if (method.equals("getVarbitValue")) {
                return varbits.getOrDefault(arguments[0], 0);
            }
            assertEquals("getDBTableField", method);
            if (arguments[1].equals(DBTableID.SailingBoatKeel.COL_FACILITY_STATS)) {
                return new Object[] {
                    arguments[0].equals(DBTableID.SailingBoatKeel.Row.SAILING_BOAT_RUNE_KEEL_2X5)
                            ? DBTableID.SailingBoatFacilityStats.Row.SAILING_BOAT_RUNE_KEEL_2X5_STATS
                            : DBTableID.SailingBoatFacilityStats.Row.SAILING_BOAT_BRONZE_KEEL_2X5_STATS
                };
            }
            return new Object[] {
                arguments[0].equals(DBTableID.SailingBoatFacilityStats.Row.SAILING_BOAT_RUNE_KEEL_2X5_STATS) ? 800 : 100
            };
        });
        TravelContext courier = new TravelContext(1, A, BoatSize.SLOOP, false, false, true, List.of(), List.of());

        assertEquals(8, tracker.damageReduction(boats, courier, courier.boatSize));
        assertEquals(1, tracker.damageReduction(boats, null, BoatSize.SLOOP));
        assertEquals(0, tracker.damageReduction(boats, null, BoatSize.RAFT));
    }

    @Test
    public void manualSelectionsAllowTeleportsWithoutGearSuppliesOrUnlocks() {
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 1);
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
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 1);
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
        varbits.put(VarbitID.SAILING_BOAT_2_TELEPORT_FOCUS, 2);
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);
        List<ActiveTask> pickup = List.of(accepted(courier(1, Port.ALDARIN, A, 1000)));

        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 0);
        assertFalse(usesSummon(plan(A, pickup)));
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 1);
        assertTrue(usesSummon(plan(A, pickup)));

        List<ActiveTask> delivery = List.of(loaded(courier(1, A, C, 1000)));
        assertFalse(plan(B, delivery).available);
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 2);
        RoutePlan returning = plan(B, delivery);
        assertTrue(returning.available);
        assertEquals(TravelStep.Kind.TELEPORT, returning.legs.get(0).steps.get(0).kind);
        assertEquals(
                A.navigationLocation, returning.legs.get(0).steps.get(0).points.get(0));
    }

    @Test
    public void parkedBoatWithGreaterFocusEnablesTeleportWithoutBoardingIt() {
        teleportsEnabled = true;
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 1);
        varbits.put(VarbitID.SAILING_BOAT_5_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_5_PORT, D.ordinal());
        varbits.put(VarbitID.SAILING_BOAT_5_TELEPORT_FOCUS, 2);
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);

        RoutePlan route = plan(A, List.of(accepted(courier(1, D, A, 1000))));

        assertTrue(route.available);
        assertEquals(TravelStep.Kind.TELEPORT, route.legs.get(0).steps.get(0).kind);
        assertEquals(D.navigationLocation, route.legs.get(0).steps.get(0).points.get(0));
        assertEquals(TravelStep.Kind.SUMMON, route.legs.get(0).steps.get(1).kind);
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
