package com.harbourmaster.tracker;

import com.harbourmaster.data.BoatSize;
import com.harbourmaster.data.CharterRoutes;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.TravelContext;
import com.harbourmaster.model.TravelMethod;
import com.harbourmaster.model.TravelStep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

public final class TravelTracker {
    private static final int[] OWNED = {
        VarbitID.SAILING_BOAT_1_OWNED,
        VarbitID.SAILING_BOAT_2_OWNED,
        VarbitID.SAILING_BOAT_3_OWNED,
        VarbitID.SAILING_BOAT_4_OWNED,
        VarbitID.SAILING_BOAT_5_OWNED
    };
    private static final int[] DOCK = {
        VarbitID.SAILING_BOAT_1_PORT,
        VarbitID.SAILING_BOAT_2_PORT,
        VarbitID.SAILING_BOAT_3_PORT,
        VarbitID.SAILING_BOAT_4_PORT,
        VarbitID.SAILING_BOAT_5_PORT
    };
    private static final int[] FOCUS = {
        VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS,
        VarbitID.SAILING_BOAT_2_TELEPORT_FOCUS,
        VarbitID.SAILING_BOAT_3_TELEPORT_FOCUS,
        VarbitID.SAILING_BOAT_4_TELEPORT_FOCUS,
        VarbitID.SAILING_BOAT_5_TELEPORT_FOCUS
    };
    private static final int[] KEEL = {
        VarbitID.SAILING_BOAT_1_KEEL,
        VarbitID.SAILING_BOAT_2_KEEL,
        VarbitID.SAILING_BOAT_3_KEEL,
        VarbitID.SAILING_BOAT_4_KEEL,
        VarbitID.SAILING_BOAT_5_KEEL
    };
    private static final int[] TYPE = {
        VarbitID.SAILING_BOAT_1_TYPE,
        VarbitID.SAILING_BOAT_2_TYPE,
        VarbitID.SAILING_BOAT_3_TYPE,
        VarbitID.SAILING_BOAT_4_TYPE,
        VarbitID.SAILING_BOAT_5_TYPE
    };
    private static final int[] HOLDS = {
        InventoryID.SAILING_BOAT_1_CARGOHOLD,
        InventoryID.SAILING_BOAT_2_CARGOHOLD,
        InventoryID.SAILING_BOAT_3_CARGOHOLD,
        InventoryID.SAILING_BOAT_4_CARGOHOLD,
        InventoryID.SAILING_BOAT_5_CARGOHOLD
    };
    private final Map<Integer, Port> docks = new HashMap<>();
    private final Map<Integer, List<Item>> contents = new HashMap<>();
    private final Map<WorldPoint, Port> arrivals = new HashMap<>();
    private int courierBoat;

    public void clear() {
        contents.clear();
        arrivals.clear();
        courierBoat = 0;
    }

    public boolean shortcutsAvailable(Client client, TravelContext travel) {
        int boat = travel == null ? client.getVarbitValue(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED) : travel.boat;
        int trial = client.getVarbitValue(VarbitID.SAILING_BT_IN_TRIAL);
        // Adamant is keel option 4. Access to these waters requires it, not an active trial.
        return boat >= 1
                && boat <= 5
                && client.getVarbitValue(KEEL[boat - 1]) >= 4
                && (trial == 0 || trial == 4)
                && client.getVarbitValue(VarbitID.SAILING_BT_GWENITH_GLIDE_MASTER_STATE) != 2;
    }

    public TravelContext read(Client client, List<ActiveTask> tasks) {
        if (docks.isEmpty()) {
            for (Port port : Port.values()) {
                docks.put(
                        (Integer) client.getDBTableField(port.databaseRow, DBTableID.SailingDock.COL_DOCK_ID, 0)[0],
                        port);
            }
        }
        int lastBoat = client.getVarbitValue(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED);
        if (courierBoat == 0 || tasks.stream().noneMatch(task -> task.carried() > 0)) {
            courierBoat = lastBoat;
        }
        boolean aboard = client.getLocalPlayer() != null
                && !client.getLocalPlayer().getWorldView().isTopLevel();
        boolean atSea = aboard && client.getVarbitValue(VarbitID.SAILING_TRANSMIT_IS_AT_SEA) != 0;
        TravelSupplies supplies = new TravelSupplies(client);
        if (supplies.has(ItemID.SAILORS_AMULET)) {
            supplies.items.put(ItemID.SAILORS_AMULET, client.getVarbitValue(VarbitID.CHARGES_SAILORS_AMULET_QUANTITY));
        }
        int crystalCharges = 0;
        int[] crystals = {
            ItemID.MOURNING_TELEPORT_CRYSTAL_1,
            ItemID.MOURNING_TELEPORT_CRYSTAL_2,
            ItemID.MOURNING_TELEPORT_CRYSTAL_3,
            ItemID.MOURNING_TELEPORT_CRYSTAL_4,
            ItemID.MOURNING_TELEPORT_CRYSTAL_5
        };
        for (int index = 0; index < crystals.length; index++) {
            crystalCharges += supplies.items.getOrDefault(crystals[index], 0) * (index + 1);
        }
        supplies.items.put(ItemID.MOURNING_TELEPORT_CRYSTAL_1, crystalCharges);
        List<TravelMethod> methods = new ArrayList<>();
        List<TravelMethod> summons = new ArrayList<>();
        if (client.getVarbitValue(VarbitID.SAILORS_AMULET_DEEPFIN) != 0) {
            item(methods, supplies, Port.DEEPFIN_POINT, ItemID.SAILORS_AMULET, "Sailors' amulet", 1943, 2757);
        }
        if (client.getVarbitValue(VarbitID.SAILORS_AMULET_ROBERTS) != 0) {
            item(methods, supplies, Port.PORT_ROBERTS, ItemID.SAILORS_AMULET, "Sailors' amulet", 1889, 3292);
        }
        item(methods, supplies, Port.PANDEMONIUM, ItemID.SAILORS_AMULET, "Sailors' amulet", 3058, 2975);
        if (client.getVarbitValue(VarbitID.VARLAMORE_VISITED) != 0) {
            house(client, methods, supplies, Port.ALDARIN, 9, ItemID.NZONE_TELETAB_ALDARIN, 1422, 2963);
        }
        if (client.getVarbitValue(VarbitID.SOTE) >= 200) {
            house(client, methods, supplies, Port.PRIFDDINAS, 7, ItemID.NZONE_TELETAB_PRIFDDINAS, 3239, 6076);
            item(
                    methods,
                    supplies,
                    Port.PRIFDDINAS,
                    ItemID.MOURNING_TELEPORT_CRYSTAL_1,
                    "Teleport crystal",
                    3264,
                    6066);
            if (supplies.has(ItemID.PRIF_TELEPORT_CRYSTAL)) {
                methods.add(teleport(Port.PRIFDDINAS, "Eternal teleport crystal", 3264, 6066, Map.of()));
            }
        }
        if (client.getVarbitValue(VarbitID.LUNAR_QUEST_MAIN) >= 190) {
            item(
                    methods,
                    supplies,
                    Port.LUNAR_ISLE,
                    ItemID.LUNAR_TABLET_MOONCLAN_TELEPORT,
                    "Moonclan tablet",
                    2113,
                    3915);
            item(methods, supplies, Port.LUNAR_ISLE, ItemID.TELEPORTSCROLL_LUNARISLE, "Lunar isle scroll", 2095, 3913);
            spell(
                    client,
                    methods,
                    supplies,
                    Port.LUNAR_ISLE,
                    2,
                    69,
                    "Moonclan teleport",
                    2113,
                    3915,
                    Map.of(ItemID.ASTRALRUNE, 2, ItemID.LAWRUNE, 1, ItemID.EARTHRUNE, 2));
        }
        for (int boat = 1; boat <= 5; boat++) {
            if (client.getVarbitValue(OWNED[boat - 1]) == 0) {
                continue;
            }
            ItemContainer hold = client.getItemContainer(HOLDS[boat - 1]);
            if (hold != null) {
                contents.put(boat, List.of(hold.getItems()));
            }
            Port port = docks.get(client.getVarbitValue(DOCK[boat - 1]));
            int focus = client.getVarbitValue(FOCUS[boat - 1]);
            if (focus == 2 && port != null && !(atSea && boat == lastBoat)) {
                boatMethods(client, methods, supplies, boat, port, false);
            }
            if (boat == courierBoat && focus > 0 && port != null) {
                boatMethods(client, summons, supplies, boat, port, true);
            }
        }
        CharterRoutes.add(
                client,
                methods,
                supplies.items.getOrDefault(ItemID.COINS, 0),
                supplies.equipped.contains(ItemID.RING_OF_CHAROS_UNLOCKED));
        Set<Integer> cargo = tasks.stream()
                .filter(task -> task.definition != null)
                .map(task -> task.definition.itemId)
                .collect(Collectors.toSet());
        boolean carrying = false;
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        if (worn != null) {
            for (Item item : worn.getItems()) {
                carrying |= cargo.contains(item.getId());
            }
        }
        for (int amount : new int[] {
            VarbitID.SAILING_CREW_HELD_CARGO_0_AMOUNT,
            VarbitID.SAILING_CREW_HELD_CARGO_1_AMOUNT,
            VarbitID.SAILING_CREW_HELD_CARGO_2_AMOUNT,
            VarbitID.SAILING_CREW_HELD_CARGO_3_AMOUNT,
            VarbitID.SAILING_CREW_HELD_CARGO_4_AMOUNT
        }) {
            carrying |= client.getVarbitValue(amount) > 0;
        }
        boolean safe = contents.containsKey(courierBoat)
                && contents.get(courierBoat).stream()
                        .noneMatch(item -> item.getId() > 0
                                && item.getQuantity() > 0
                                && !cargo.contains(item.getId())
                                && !safeHoldItem(client, item.getId()));
        Map<Integer, Integer> relevant = new HashMap<>();
        for (TravelMethod method : methods) {
            if (method.arrival != null) {
                arrivals.put(method.arrival, method.destination);
            }
            method.cost.keySet().forEach(item -> relevant.put(item, supplies.items.getOrDefault(item, 0)));
        }
        for (TravelMethod method : summons) {
            method.cost.keySet().forEach(item -> relevant.put(item, supplies.items.getOrDefault(item, 0)));
        }
        return new TravelContext(
                courierBoat,
                courierBoat >= 1 && courierBoat <= 5 && !(atSea && lastBoat == courierBoat)
                        ? docks.get(client.getVarbitValue(DOCK[courierBoat - 1]))
                        : null,
                courierBoat >= 1 && courierBoat <= 5
                        ? BoatSize.values()[client.getVarbitValue(TYPE[courierBoat - 1])]
                        : null,
                aboard && lastBoat == courierBoat,
                carrying,
                safe,
                methods,
                summons,
                relevant);
    }

    private static boolean safeHoldItem(Client client, int item) {
        String name = client.getItemDefinition(item).getName().toLowerCase(java.util.Locale.ROOT);
        return name.equals("captain's log")
                || name.endsWith("repair kit")
                || name.endsWith("cannonball")
                || name.equals("rope");
    }

    public Port playerPort(Client client, PortTracker ports, TravelContext travel) {
        if (travel.aboard || ports.getDock() != null) {
            return ports.getStart();
        }
        WorldPoint position = PortTracker.position(client);
        if (position == null) {
            return ports.getStart();
        }
        Port closest = null;
        int distance = 128;
        for (Port port : Port.values()) {
            int candidate = position.distanceTo2D(port.navigationLocation);
            if (candidate < distance) {
                distance = candidate;
                closest = port;
            }
        }
        for (Map.Entry<WorldPoint, Port> arrival : arrivals.entrySet()) {
            if (position.distanceTo2D(arrival.getKey()) < distance) {
                distance = position.distanceTo2D(arrival.getKey());
                closest = arrival.getValue();
            }
        }
        return closest;
    }

    private static void house(
            Client client,
            List<TravelMethod> methods,
            TravelSupplies supplies,
            Port port,
            int house,
            int tablet,
            int x,
            int y) {
        item(methods, supplies, port, tablet, port.name + " tablet", x, y);
        if (supplies.has(ItemID.SKILLCAPE_CONSTRUCTION, ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED)) {
            methods.add(teleport(port, "Construction cape", x, y, Map.of()));
        }
        if (client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION) == house) {
            item(methods, supplies, port, ItemID.POH_TABLET_TELEPORTTOHOUSE, "House tablet outside", x, y);
            spell(
                    client,
                    methods,
                    supplies,
                    port,
                    0,
                    40,
                    "Teleport to house outside",
                    x,
                    y,
                    Map.of(ItemID.LAWRUNE, 1, ItemID.AIRRUNE, 1, ItemID.EARTHRUNE, 1));
        }
    }

    private static void item(
            List<TravelMethod> methods, TravelSupplies supplies, Port port, int item, String name, int x, int y) {
        if (supplies.has(item)) {
            methods.add(teleport(port, name, x, y, Map.of(item, 1)));
        }
    }

    private static TravelMethod teleport(Port port, String name, int x, int y, Map<Integer, Integer> cost) {
        WorldPoint arrival = new WorldPoint(x, y, 0);
        // Prifddinas city uses a separate map area; a straight coordinate distance to its dock is meaningless.
        double walk = port == Port.PRIFDDINAS ? 80 : arrival.distanceTo2D(port.navigationLocation) / 2.0 + 2;
        return new TravelMethod(null, port, 0, TravelStep.Kind.TELEPORT, "Use " + name, 4, arrival, walk, cost);
    }

    private static void spell(
            Client client,
            List<TravelMethod> methods,
            TravelSupplies supplies,
            Port port,
            int book,
            int level,
            String name,
            int x,
            int y,
            Map<Integer, Integer> cost) {
        if (client.getVarbitValue(VarbitID.SPELLBOOK) == book && client.getBoostedSkillLevel(Skill.MAGIC) >= level) {
            for (Map<Integer, Integer> allocated : supplies.spellCosts(cost)) {
                methods.add(teleport(port, name, x, y, allocated));
            }
        }
    }

    private static void boatMethods(
            Client client, List<TravelMethod> methods, TravelSupplies supplies, int boat, Port port, boolean summon) {
        if (client.getVarbitValue(VarbitID.SAILING_INTRO) < 50) {
            return;
        }
        int tablet = summon ? ItemID.POH_TABLET_TELEPORTBOATTOME : ItemID.POH_TABLET_TELEPORTMETOBOAT;
        TravelStep.Kind kind = summon ? TravelStep.Kind.SUMMON : TravelStep.Kind.TELEPORT;
        String instruction = summon ? "Summon boat " + boat : "Teleport to boat " + boat;
        if (supplies.has(tablet)) {
            methods.add(new TravelMethod(
                    null, port, boat, kind, instruction + " using a tablet", 5, null, Map.of(tablet, 1)));
        }
        if (client.getVarbitValue(VarbitID.SPELLBOOK) == 0
                && client.getBoostedSkillLevel(Skill.MAGIC) >= (summon ? 56 : 67)) {
            for (Map<Integer, Integer> cost : supplies.spellCosts(
                    Map.of(ItemID.LAWRUNE, 2, ItemID.WATERRUNE, summon ? 1 : 2, ItemID.EARTHRUNE, summon ? 1 : 2))) {
                methods.add(new TravelMethod(null, port, boat, kind, instruction, 5, null, cost));
            }
        }
    }
}
