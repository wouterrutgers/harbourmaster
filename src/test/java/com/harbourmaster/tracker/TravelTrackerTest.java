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
        assertTrue(usesSummon(plan(tasks)));

        teleportsEnabled = false;
        containers.put(InventoryID.INV, new Item[] {
            new Item(ItemID.NZONE_TELETAB_ALDARIN, 10), new Item(ItemID.POH_TABLET_TELEPORTBOATTOME, 10)
        });
        assertFalse(usesSummon(plan(tasks)));
    }

    @Test
    public void enabledAmuletStillRequiresKnownBoatContentsBeforeSummoning() {
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        teleportsEnabled = true;
        List<ActiveTask> tasks = List.of(accepted(courier(1, Port.DEEPFIN_POINT, A, 1000)));

        assertFalse(usesSummon(plan(tasks)));
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);
        assertTrue(usesSummon(plan(tasks)));
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[] {new Item(ItemID.STAFF_OF_AIR, 1)});
        assertFalse(usesSummon(plan(tasks)));
    }

    private RoutePlan plan(List<ActiveTask> tasks) {
        PortGraph graph = new PortGraph(
                        (from, to, size) -> Optional.of(new RouteLeg(null, null, 1000, List.of(from, to))))
                .detachedSnapshot(null);
        return new RouteOptimizer(graph, tracker.read(client, tasks, config)).optimize(A, tasks);
    }

    private static boolean usesSummon(RoutePlan plan) {
        assertTrue(plan.available);
        return plan.legs.stream()
                .flatMap(leg -> leg.steps.stream())
                .anyMatch(step -> step.kind == TravelStep.Kind.SUMMON);
    }
}
