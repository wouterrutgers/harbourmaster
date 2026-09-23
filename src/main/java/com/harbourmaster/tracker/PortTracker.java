package com.harbourmaster.tracker;

import com.harbourmaster.data.CargoHoldObjects;
import com.harbourmaster.model.Port;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;

public final class PortTracker {
    private static final int DOCK_APPROACH_DISTANCE = 3;
    private final Set<GameObject> objects = new HashSet<>();
    private Port dock;
    private Port associated;
    private Port start;
    private WorldPoint boatPosition;

    public void add(GameObject object) {
        if (Port.fromObject(object.getId()) != null
                || CargoHoldObjects.IDS.contains(object.getId())
                || object.getId() == ObjectID.SAILING_GANGPLANK_PROXY) {
            objects.add(object);
        }
    }

    public void remove(GameObject object) {
        objects.remove(object);
    }

    public void unload(WorldView view) {
        objects.removeIf(object -> object.getWorldView() == view);
    }

    public void scanScene(Client client) {
        objects.clear();
        WorldView top = client.getTopLevelWorldView();
        if (top == null) {
            return;
        }
        scan(top);
        for (WorldEntity entity : top.worldEntities()) {
            scan(entity.getWorldView());
        }
    }

    private void scan(WorldView view) {
        if (view == null) {
            return;
        }
        for (Tile[][] plane : view.getScene().getTiles()) {
            for (Tile[] column : plane) {
                for (Tile tile : column) {
                    if (tile == null) {
                        continue;
                    }
                    for (GameObject object : tile.getGameObjects()) {
                        if (object != null) {
                            add(object);
                        }
                    }
                }
            }
        }
    }

    public void update(Client client, Port board) {
        dock = null;
        WorldPoint location = position(client);
        if (location == null && board == null) {
            return;
        }
        boolean atSea = client.getVarbitValue(VarbitID.SAILING_TRANSMIT_IS_AT_SEA) != 0;
        boolean aboard = client.getLocalPlayer() != null
                && !client.getLocalPlayer().getWorldView().isTopLevel();
        boatPosition = aboard && board == null ? position(client, true) : null;
        int closestObject = 25;
        for (GameObject object : objects) {
            Port port = Port.fromObject(object.getId());
            if (location == null || port == null || !object.getWorldView().isTopLevel()) {
                continue;
            }
            int approachDistance =
                    boatPosition == null ? Integer.MAX_VALUE : boatPosition.distanceTo(port.navigationLocation);
            if (atSea && approachDistance > DOCK_APPROACH_DISTANCE) {
                continue;
            }
            int distance = approachDistance <= DOCK_APPROACH_DISTANCE
                    ? approachDistance
                    : location.distanceTo(WorldPoint.fromLocalInstance(client, object.getLocalLocation()));
            if (distance < closestObject) {
                closestObject = distance;
                dock = port;
            }
        }
        if (dock != null) {
            associated = dock;
        }
        if (board != null) {
            start = board;
            associated = board;
        } else if (dock != null) {
            start = dock;
        } else if (associated != null) {
            start = associated;
        } else {
            int nearest = 257;
            start = null;
            for (Port port : Port.values()) {
                int distance = location.distanceTo(port.navigationLocation);
                if (distance < nearest) {
                    nearest = distance;
                    start = port;
                }
            }
            if (atSea || aboard) {
                associated = start;
            }
        }
    }

    public static WorldPoint position(Client client) {
        return position(client, false);
    }

    private static WorldPoint position(Client client, boolean boatAnchor) {
        Player player = client.getLocalPlayer();
        WorldView top = client.getTopLevelWorldView();
        if (player == null || top == null) {
            return null;
        }
        LocalPoint location = player.getLocalLocation();
        if (!player.getWorldView().isTopLevel()) {
            WorldEntity entity =
                    top.worldEntities().byIndex(player.getWorldView().getId());
            if (entity == null) {
                return null;
            }
            location = boatAnchor ? entity.getLocalLocation() : entity.transformToMainWorld(location);
        }
        return WorldPoint.fromLocalInstance(client, location);
    }

    public void clear() {
        objects.clear();
        dock = null;
        associated = null;
        start = null;
        boatPosition = null;
    }

    public List<GameObject> getObjects() {
        return List.copyOf(objects);
    }

    public Port getDock() {
        return dock;
    }

    public WorldPoint getBoatPosition() {
        return boatPosition;
    }

    public Port getStart() {
        return start;
    }
}
