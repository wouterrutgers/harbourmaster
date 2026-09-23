package com.harbourmaster.overlay;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.HarbourmasterConfig;
import com.harbourmaster.HarbourmasterPlugin;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.DockChecklist;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.tracker.CargoTracker;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

public class DockOverlayTest {
    private final List<ActiveTask> tasks = List.of(loaded(courier(1, A, B, 100)));
    private final CargoTracker cargo = new CargoTracker();
    private Item carried;
    private Port dock = A;
    private final WorldView boat = boat();
    private WorldView playerWorld = boat;
    private final Player player = ApiStub.of(Player.class, (method, arguments) -> {
        if (method.equals("getWorldView")) {
            return playerWorld;
        }
        throw new AssertionError(method);
    });
    private final ItemContainer equipment = ApiStub.of(ItemContainer.class, (method, arguments) -> {
        if (method.equals("getItem")) {
            assertEquals(EquipmentInventorySlot.WEAPON.getSlotIdx(), arguments[0]);
            return carried;
        }
        throw new AssertionError(method);
    });
    private final Client client = ApiStub.of(Client.class, (method, arguments) -> {
        switch (method) {
            case "getLocalPlayer":
                return player;
            case "getItemContainer":
                assertEquals(InventoryID.WORN, arguments[0]);
                return equipment;
            default:
                throw new AssertionError(method);
        }
    });
    private final HarbourmasterPlugin plugin = new HarbourmasterPlugin() {
        @Override
        public HarbourmasterSnapshot getSnapshot() {
            Map<Integer, CargoTracker.Destination> destinations = cargo.destinations(tasks, dock);
            return new HarbourmasterSnapshot(
                    true,
                    RoutePlan.empty(),
                    List.of(),
                    false,
                    4,
                    DockChecklist.at(dock, tasks),
                    destinations,
                    cargo.needsDeposit(client, destinations, dock));
        }
    };

    @Test
    public void carriedCrateHighlightsOwnHoldAfterPickupAndClearsAfterDeposit() {
        plugin.getPorts().add(hold(boat, 10));
        plugin.getPorts().add(hold(boat(), 70));
        carried = new Item(tasks.get(0).definition.itemId, 1);
        BufferedImage carrying = render();
        assertNotEquals(0, carrying.getRGB(20, 20));
        assertEquals(0, carrying.getRGB(80, 20));

        dock = null;
        assertNotEquals(0, render().getRGB(20, 20));

        carried = null;
        assertEquals(0, render().getRGB(20, 20));
        carried = new Item(999, 1);
        assertEquals(0, render().getRGB(20, 20));
    }

    @Test
    public void gangplankFollowsBoardingAndDockActionsWithoutMarkingOtherBoats() {
        WorldView shore = world(true);
        plugin.getPorts().add(object(shore, A.gangplankObject, 10));
        plugin.getPorts().add(object(shore, B.gangplankObject, 70));
        playerWorld = shore;
        carried = new Item(tasks.get(0).definition.itemId, 1);
        assertNotEquals(0, render().getRGB(20, 20));
        assertEquals(0, render().getRGB(80, 20));

        playerWorld = boat;
        assertEquals(0, render().getRGB(20, 20));
        plugin.getPorts().clear();
        plugin.getPorts().add(object(boat, ObjectID.SAILING_GANGPLANK_PROXY, 10));
        plugin.getPorts().add(object(boat(), ObjectID.SAILING_GANGPLANK_PROXY, 70));
        assertEquals(0, render().getRGB(20, 20));

        dock = B;
        assertNotEquals(0, render().getRGB(20, 20));
        assertEquals(0, render().getRGB(80, 20));
        dock = null;
        assertEquals(0, render().getRGB(20, 20));
    }

    @Test
    public void disembarkingCanUseThePortsShoreGangplank() {
        dock = B;
        WorldView shore = world(true);
        plugin.getPorts().add(object(shore, B.gangplankObject, 10));
        plugin.getPorts().add(object(shore, A.gangplankObject, 70));
        assertNotEquals(0, render().getRGB(20, 20));
        assertEquals(0, render().getRGB(80, 20));
    }

    private BufferedImage render() {
        BufferedImage image = new BufferedImage(120, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            new DockOverlay(client, plugin, new HarbourmasterConfig() {}).render(graphics);
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private static WorldView boat() {
        return world(false, 1);
    }

    private static WorldView world(boolean topLevel) {
        return world(topLevel, 0);
    }

    private static WorldView world(boolean topLevel, int plane) {
        Tile[][][] tiles = new Tile[4][2][1];
        Scene scene = ApiStub.of(Scene.class, (method, arguments) -> {
            if (method.equals("getTiles")) {
                return tiles;
            }
            throw new AssertionError(method);
        });
        return ApiStub.of(WorldView.class, (method, arguments) -> {
            switch (method) {
                case "isTopLevel":
                    return topLevel;
                case "getPlane":
                    return plane;
                case "getScene":
                    return scene;
                default:
                    throw new AssertionError(method);
            }
        });
    }

    private static GameObject hold(WorldView boat, int horizontalPosition) {
        return object(boat, ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT, horizontalPosition);
    }

    private static GameObject object(WorldView world, int identifier, int horizontalPosition) {
        boolean gangplank = identifier == ObjectID.SAILING_GANGPLANK_PROXY
                || Port.fromObject(identifier) != null && Port.fromObject(identifier).gangplankObject == identifier;
        GroundObject ground = ApiStub.of(GroundObject.class, (method, arguments) -> {
            switch (method) {
                case "getConvexHull":
                    return new Rectangle(horizontalPosition, 10, 20, 20);
                case "getCanvasTextLocation":
                    return null;
                default:
                    throw new AssertionError(method);
            }
        });
        world.getScene().getTiles()[0][horizontalPosition / 60][0] = ApiStub.of(Tile.class, (method, arguments) -> {
            if (method.equals("getGroundObject")) {
                return ground;
            }
            throw new AssertionError(method);
        });
        return ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return identifier;
                case "getWorldView":
                    return world;
                case "getPlane":
                    return world.getPlane();
                case "getLocalLocation":
                    return new LocalPoint(horizontalPosition / 60 * 128 + 64, 64, 0);
                case "getConvexHull":
                    return gangplank ? null : new Rectangle(horizontalPosition, 10, 20, 20);
                case "getCanvasTextLocation":
                    return null;
                default:
                    throw new AssertionError(method);
            }
        });
    }
}
