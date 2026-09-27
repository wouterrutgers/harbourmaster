package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.*;
import com.harbourmaster.optimizer.RouteOptimizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class TravelTrackerTest {
    private final TravelTracker tracker = new TravelTracker();
    private final Map<Integer, Integer> varbits = new HashMap<>();
    private final Map<Integer, Item[]> containers = new HashMap<>();
    private final Client client = ApiStub.of(Client.class, (method, arguments) -> {
        switch (method) {
            case "getVarbitValue":
                return varbits.getOrDefault(arguments[0], 0);
            case "getVarpValue":
                return 0;
            case "getBoostedSkillLevel":
                return 99;
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
            case "getEnum":
                return ApiStub.of(EnumComposition.class, (operation, parameters) -> {
                    assertEquals("getIntValue", operation);
                    return parameters[0].equals(1) ? ItemID.LAWRUNE : ItemID.MUDRUNE;
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
    public void detectedSpellsSharePouchRunesAndRespectEquippedStaffAndSpellbook() {
        varbits.put(VarbitID.SAILING_INTRO, 50);
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 1);
        varbits.put(VarbitID.POH_HOUSE_LOCATION, 9);
        varbits.put(VarbitID.VARLAMORE_VISITED, 1);
        varbits.put(VarbitID.RUNE_POUCH_TYPE_1, 1);
        varbits.put(VarbitID.RUNE_POUCH_QUANTITY_1, 3);
        varbits.put(VarbitID.RUNE_POUCH_TYPE_2, 2);
        varbits.put(VarbitID.RUNE_POUCH_QUANTITY_2, 2);
        containers.put(InventoryID.INV, new Item[] {new Item(ItemID.BH_RUNE_POUCH, 1)});
        containers.put(InventoryID.WORN, new Item[] {new Item(ItemID.STAFF_OF_AIR, 1)});
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);
        List<ActiveTask> tasks = List.of(accepted(courier(1, Port.ALDARIN, A, 1000)));

        assertTrue(usesSummon(plan(tasks)));
        varbits.put(VarbitID.RUNE_POUCH_QUANTITY_1, 2);
        assertFalse(usesSummon(plan(tasks)));
        varbits.put(VarbitID.RUNE_POUCH_QUANTITY_1, 3);
        varbits.put(VarbitID.SPELLBOOK, 2);
        assertFalse(usesSummon(plan(tasks)));
    }

    @Test
    public void chargedAmuletNeedsItsUnlockAndBoatContentsMustBeKnownBeforeSummoning() {
        varbits.put(VarbitID.SAILING_INTRO, 50);
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 1);
        varbits.put(VarbitID.CHARGES_SAILORS_AMULET_QUANTITY, 1);
        containers.put(
                InventoryID.INV,
                new Item[] {new Item(ItemID.SAILORS_AMULET, 1), new Item(ItemID.POH_TABLET_TELEPORTBOATTOME, 1)});
        List<ActiveTask> tasks = List.of(accepted(courier(1, Port.DEEPFIN_POINT, A, 1000)));
        assertFalse(usesSummon(plan(tasks)));
        varbits.put(VarbitID.SAILORS_AMULET_DEEPFIN, 1);
        assertFalse(usesSummon(plan(tasks)));
        containers.put(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item[0]);
        assertTrue(usesSummon(plan(tasks)));
        varbits.put(VarbitID.CHARGES_SAILORS_AMULET_QUANTITY, 0);
        assertFalse(usesSummon(plan(tasks)));
    }

    private RoutePlan plan(List<ActiveTask> tasks) {
        PortGraph graph = new PortGraph(
                        (from, to, size) -> Optional.of(new RouteLeg(null, null, 1000, List.of(from, to))))
                .detachedSnapshot(null);
        return new RouteOptimizer(graph, tracker.read(client, tasks)).optimize(A, tasks);
    }

    private static boolean usesSummon(RoutePlan plan) {
        assertTrue(plan.available);
        return plan.legs.stream()
                .flatMap(leg -> leg.steps.stream())
                .anyMatch(step -> step.kind == TravelStep.Kind.SUMMON);
    }
}
