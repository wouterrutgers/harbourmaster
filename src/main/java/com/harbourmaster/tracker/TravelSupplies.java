package com.harbourmaster.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

final class TravelSupplies {
    private static final int[] POUCH_TYPES = {
        VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4
    };
    private static final int[] POUCH_QUANTITIES = {
        VarbitID.RUNE_POUCH_QUANTITY_1,
        VarbitID.RUNE_POUCH_QUANTITY_2,
        VarbitID.RUNE_POUCH_QUANTITY_3,
        VarbitID.RUNE_POUCH_QUANTITY_4
    };
    private static final Map<Integer, Set<Integer>> ELEMENTS = Map.of(
            ItemID.AIRRUNE,
            Set.of(ItemID.AIRRUNE),
            ItemID.WATERRUNE,
            Set.of(ItemID.WATERRUNE),
            ItemID.EARTHRUNE,
            Set.of(ItemID.EARTHRUNE),
            ItemID.MISTRUNE,
            Set.of(ItemID.AIRRUNE, ItemID.WATERRUNE),
            ItemID.DUSTRUNE,
            Set.of(ItemID.AIRRUNE, ItemID.EARTHRUNE),
            ItemID.MUDRUNE,
            Set.of(ItemID.WATERRUNE, ItemID.EARTHRUNE),
            ItemID.SMOKERUNE,
            Set.of(ItemID.AIRRUNE),
            ItemID.STEAMRUNE,
            Set.of(ItemID.WATERRUNE),
            ItemID.LAVARUNE,
            Set.of(ItemID.EARTHRUNE));
    final Map<Integer, Integer> items = new HashMap<>();
    final Set<Integer> equipped = new LinkedHashSet<>();
    private final Set<Integer> unlimited = new LinkedHashSet<>();

    TravelSupplies(Client client) {
        read(client.getItemContainer(InventoryID.INV), false);
        read(client.getItemContainer(InventoryID.WORN), true);
        if (has(
                ItemID.BH_RUNE_POUCH,
                ItemID.BH_RUNE_POUCH_TROUVER,
                ItemID.DIVINE_RUNE_POUCH,
                ItemID.DIVINE_RUNE_POUCH_TROUVER)) {
            for (int slot = 0; slot < POUCH_TYPES.length; slot++) {
                int type = client.getVarbitValue(POUCH_TYPES[slot]);
                if (type != 0) {
                    items.merge(
                            client.getEnum(EnumID.RUNEPOUCH_RUNE).getIntValue(type),
                            client.getVarbitValue(POUCH_QUANTITIES[slot]),
                            Integer::sum);
                }
            }
        }
        for (int item : equipped) {
            // Charged providers only. Empty tomes do not supply runes.
            String name = client.getItemDefinition(item).getName().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("staff of air")
                    || name.contains("air battlestaff")
                    || name.equals("mystic air staff")
                    || name.contains("mist battlestaff")
                    || name.contains("dust battlestaff")
                    || name.contains("smoke battlestaff")
                    || name.contains("mystic mist staff")
                    || name.contains("mystic dust staff")
                    || name.contains("mystic smoke staff")) {
                unlimited.add(ItemID.AIRRUNE);
            }
            if (name.equals("staff of water")
                    || name.contains("water battlestaff")
                    || name.equals("mystic water staff")
                    || name.contains("mist battlestaff")
                    || name.contains("mud battlestaff")
                    || name.contains("steam battlestaff")
                    || name.contains("mystic mist staff")
                    || name.contains("mystic mud staff")
                    || name.contains("mystic steam staff")
                    || item == ItemID.TOME_OF_WATER
                    || name.equals("kodai wand")) {
                unlimited.add(ItemID.WATERRUNE);
            }
            if (name.equals("staff of earth")
                    || name.contains("earth battlestaff")
                    || name.equals("mystic earth staff")
                    || name.contains("dust battlestaff")
                    || name.contains("mud battlestaff")
                    || name.contains("lava battlestaff")
                    || name.contains("mystic dust staff")
                    || name.contains("mystic mud staff")
                    || name.contains("mystic lava staff")
                    || item == ItemID.TOME_OF_EARTH) {
                unlimited.add(ItemID.EARTHRUNE);
            }
        }
    }

    private void read(ItemContainer container, boolean worn) {
        if (container == null) {
            return;
        }
        for (Item item : container.getItems()) {
            if (item.getId() > 0 && item.getQuantity() > 0) {
                items.merge(item.getId(), item.getQuantity(), Integer::sum);
                if (worn) {
                    equipped.add(item.getId());
                }
            }
        }
    }

    boolean has(int... identifiers) {
        for (int identifier : identifiers) {
            if (items.getOrDefault(identifier, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    List<Map<Integer, Integer>> spellCosts(Map<Integer, Integer> required) {
        Map<Integer, Integer> remaining = new HashMap<>(required);
        unlimited.forEach(remaining::remove);
        Set<Map<Integer, Integer>> costs = new LinkedHashSet<>();
        allocate(remaining, new HashMap<>(), costs);
        return new ArrayList<>(costs);
    }

    private void allocate(
            Map<Integer, Integer> required, Map<Integer, Integer> cost, Set<Map<Integer, Integer>> result) {
        if (required.isEmpty()) {
            result.add(Map.copyOf(cost));
            return;
        }
        int rune = required.keySet().stream().min(Integer::compareTo).orElseThrow();
        for (int candidate : items.keySet()) {
            Set<Integer> elements = ELEMENTS.getOrDefault(candidate, Set.of(candidate));
            if (!elements.contains(rune) || cost.getOrDefault(candidate, 0) >= items.get(candidate)) {
                continue;
            }
            Map<Integer, Integer> next = new HashMap<>(required);
            for (int element : elements) {
                if (next.containsKey(element)) {
                    int count = next.get(element) - 1;
                    if (count == 0) {
                        next.remove(element);
                    } else {
                        next.put(element, count);
                    }
                }
            }
            cost.merge(candidate, 1, Integer::sum);
            allocate(next, cost, result);
            int count = cost.get(candidate) - 1;
            if (count == 0) {
                cost.remove(candidate);
            } else {
                cost.put(candidate, count);
            }
        }
    }
}
