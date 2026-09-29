package com.harbourmaster.tracker;

import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.data.BoatSize;
import com.harbourmaster.data.CharterRoutes;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.BoatFocus;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.TravelContext;
import com.harbourmaster.model.TravelMethod;
import com.harbourmaster.model.TravelStep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
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
    private final Map<Integer, Integer> savedBoatDocks = new HashMap<>();
    private final Map<Integer, Port> boatPorts = new HashMap<>();
    private final Map<Integer, List<Item>> contents = new HashMap<>();
    private final Map<WorldPoint, Port> arrivals = new HashMap<>();
    private int courierBoat;

    public void clear() {
        savedBoatDocks.clear();
        boatPorts.clear();
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

    public TravelContext read(Client client, List<ActiveTask> tasks, HarbourmasterConfig config, Port currentDock) {
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
        BoatFocus[] focuses = {
            config.boat1Focus(), config.boat2Focus(), config.boat3Focus(), config.boat4Focus(), config.boat5Focus()
        };
        Port courierPort = null;
        List<TravelMethod> methods = new ArrayList<>();
        List<TravelMethod> summons = new ArrayList<>();
        if (config.sailorsAmuletPandemonium()) {
            methods.add(teleport(Port.PANDEMONIUM, "Sailors' amulet to Pandemonium", 3058, 2975));
        }
        if (config.sailorsAmuletDeepfinPoint()) {
            methods.add(teleport(Port.DEEPFIN_POINT, "Sailors' amulet to Deepfin Point", 1943, 2757));
        }
        if (config.sailorsAmuletPortRoberts()) {
            methods.add(teleport(Port.PORT_ROBERTS, "Sailors' amulet to Port Roberts", 1889, 3292));
        }
        if (config.aldarinTeleport()) {
            methods.add(teleport(Port.ALDARIN, "Teleport to Aldarin house portal", 1422, 2963));
        }
        if (config.prifddinasTeleport()) {
            methods.add(teleport(Port.PRIFDDINAS, "Teleport to Prifddinas house portal", 3239, 6076));
        }
        if (config.teleportCrystal()) {
            methods.add(teleport(Port.PRIFDDINAS, "Teleport crystal to Prifddinas", 3264, 6066));
        }
        if (config.moonclanTeleport()) {
            methods.add(teleport(Port.LUNAR_ISLE, "Moonclan teleport", 2113, 3915));
        }
        if (config.lunarIsleScroll()) {
            methods.add(teleport(Port.LUNAR_ISLE, "Lunar Isle scroll", 2095, 3913));
        }
        for (int boat = 1; boat <= 5; boat++) {
            if (client.getVarbitValue(OWNED[boat - 1]) == 0) {
                continue;
            }
            ItemContainer hold = client.getItemContainer(HOLDS[boat - 1]);
            if (hold != null) {
                contents.put(boat, List.of(hold.getItems()));
            }
            int savedDock = client.getVarbitValue(DOCK[boat - 1]);
            if (!Objects.equals(savedBoatDocks.put(boat, savedDock), savedDock)) {
                boatPorts.put(boat, docks.get(savedDock));
            }
            // Keep an observed arrival after disembarking until the saved dock changes.
            if (aboard && boat == lastBoat) {
                boatPorts.put(boat, currentDock);
            }
            Port port = boatPorts.get(boat);
            if (boat == courierBoat) {
                courierPort = port;
            }
            if (config.teleportToBoat() && focuses[boat - 1] == BoatFocus.GREATER_TELEPORT_FOCUS && port != null) {
                methods.add(new TravelMethod(
                        null, port, boat, TravelStep.Kind.TELEPORT, "Teleport to boat " + boat, 5, null));
            }
            if (config.summonBoat() && focuses[boat - 1] != BoatFocus.NONE && boat == courierBoat && port != null) {
                summons.add(new TravelMethod(null, port, boat, TravelStep.Kind.SUMMON, "Summon boat " + boat, 5, null));
            }
        }
        if (config.charterShips()) {
            CharterRoutes.add(methods);
        }
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
        for (TravelMethod method : methods) {
            if (method.arrival != null) {
                arrivals.put(method.arrival, method.destination);
            }
        }
        return new TravelContext(
                courierBoat,
                courierPort,
                courierBoat >= 1 && courierBoat <= 5
                        ? BoatSize.values()[client.getVarbitValue(TYPE[courierBoat - 1])]
                        : null,
                aboard && lastBoat == courierBoat,
                carrying,
                safe,
                methods,
                summons);
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

    private static TravelMethod teleport(Port port, String instruction, int x, int y) {
        WorldPoint arrival = new WorldPoint(x, y, 0);
        // Prifddinas city uses a separate map area; a straight coordinate distance to its dock is meaningless.
        double walk = port == Port.PRIFDDINAS ? 80 : arrival.distanceTo2D(port.navigationLocation) / 2.0 + 2;
        return new TravelMethod(null, port, 0, TravelStep.Kind.TELEPORT, instruction, 4, arrival, walk);
    }
}
