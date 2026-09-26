package com.harbourmaster.tracker;

import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.data.BoatSize;
import com.harbourmaster.model.Port;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class PortTrackerTest {
    private final PortTracker tracker = new PortTracker();
    private WorldPoint location = Port.PORT_SARIM.navigationLocation;
    private boolean entityAvailable = true;
    private boolean instance;
    private boolean aboard = true;
    private boolean atSea;
    private final int[][][] chunks = new int[4][13][13];
    private final WorldView boat = ApiStub.of(WorldView.class, (method, arguments) -> {
        switch (method) {
            case "isTopLevel":
                return false;
            case "getId":
                return 0;
            default:
                throw new AssertionError(method);
        }
    });
    private final Scene scene = ApiStub.of(Scene.class, (method, arguments) -> {
        if (method.equals("getTiles")) {
            return new Tile[0][][];
        }
        throw new AssertionError(method);
    });
    private final WorldEntity entity = ApiStub.of(WorldEntity.class, (method, arguments) -> {
        switch (method) {
            case "getLocalLocation":
            case "transformToMainWorld":
                return new LocalPoint(64, 64, WorldView.TOPLEVEL);
            case "getWorldView":
                return null;
            default:
                throw new AssertionError(method);
        }
    });
    private final IndexedObjectSet<WorldEntity> entities = new IndexedObjectSet<WorldEntity>() {
        @Override
        public WorldEntity byIndex(int index) {
            return entityAvailable ? entity : null;
        }

        @Override
        public java.util.Iterator<WorldEntity> iterator() {
            return List.of(entity).iterator();
        }
    };
    private final WorldView world = ApiStub.of(WorldView.class, (method, arguments) -> {
        switch (method) {
            case "isInstance":
                return instance;
            case "isTopLevel":
                return true;
            case "getId":
                return WorldView.TOPLEVEL;
            case "getPlane":
                return 0;
            case "getBaseX":
                return location.getX();
            case "getBaseY":
                return location.getY();
            case "getScene":
                return scene;
            case "getInstanceTemplateChunks":
                return chunks;
            case "worldEntities":
                return entities;
            default:
                throw new AssertionError(method);
        }
    });
    private final Player player = ApiStub.of(Player.class, (method, arguments) -> {
        switch (method) {
            case "getWorldView":
                return aboard ? boat : world;
            case "getLocalLocation":
                return new LocalPoint(64, 64, aboard ? 0 : WorldView.TOPLEVEL);
            default:
                throw new AssertionError(method);
        }
    });
    private final Client client = ApiStub.of(Client.class, (method, arguments) -> {
        switch (method) {
            case "getLocalPlayer":
                return player;
            case "getTopLevelWorldView":
            case "getWorldView":
                return world;
            case "getVarbitValue":
                assertEquals(VarbitID.SAILING_TRANSMIT_IS_AT_SEA, arguments[0]);
                return atSea ? 1 : 0;
            default:
                throw new AssertionError(method);
        }
    });

    @Test
    public void voyageKeepsOriginAndBoatPositionAcrossSceneChanges() {
        tracker.update(client, Port.PORT_SARIM);
        assertNull(tracker.getBoatPosition());
        location = new WorldPoint(1000, 1000, 0);
        tracker.update(client, null);
        assertSame(Port.PORT_SARIM, tracker.getStart());
        assertEquals(location, tracker.getBoatPosition());
        entityAvailable = false;
        tracker.update(client, null);
        assertSame(Port.PORT_SARIM, tracker.getStart());
        assertEquals(location, tracker.getBoatPosition());
        entityAvailable = true;
        aboard = false;
        location = Port.PORT_SARIM.navigationLocation;
        tracker.update(client, null);
        assertSame(Port.PORT_SARIM, tracker.getStart());
        assertNull(tracker.getBoatPosition());
        tracker.update(client, Port.BRIMHAVEN);
        assertSame(Port.BRIMHAVEN, tracker.getStart());
    }

    @Test
    public void boatPositionUsesOuterInstanceCoordinates() {
        instance = true;
        chunks[0][0][0] = (320 << 14) | (400 << 3);
        assertEquals(new WorldPoint(2560, 3200, 0), PortTracker.position(client));
    }

    @Test
    public void detectsTheVesselSizeFromItsCargoHold() {
        tracker.add(ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5;
                case "getWorldView":
                    return boat;
                default:
                    throw new AssertionError(method);
            }
        }));

        tracker.update(client, null);

        assertSame(BoatSize.SKIFF, tracker.getBoatSize());
    }

    @Test
    public void usesTheNearestVisibleVesselWhenNotAboard() {
        aboard = false;
        tracker.add(ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE;
                case "getWorldView":
                    return world;
                case "getLocalLocation":
                    return new LocalPoint(64 + 10 * 128, 64, WorldView.TOPLEVEL);
                default:
                    throw new AssertionError(method);
            }
        }));
        tracker.add(ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5;
                case "getWorldView":
                    return world;
                case "getLocalLocation":
                    return new LocalPoint(64 + 2 * 128, 64, WorldView.TOPLEVEL);
                default:
                    throw new AssertionError(method);
            }
        }));

        tracker.update(client, null);

        assertSame(BoatSize.SKIFF, tracker.getBoatSize());
    }

    @Test
    public void sceneScanToleratesAnEntityWhoseWorldViewIsNotLoadedYet() {
        tracker.scanScene(client);
    }

    @Test
    public void boatAtPortRobertsRecognizesDockWithDistantBoard() {
        location = Port.PORT_ROBERTS.navigationLocation;
        atSea = true;
        tracker.add(ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return Port.PORT_ROBERTS.noticeboardObject;
                case "getWorldView":
                    return world;
                case "getLocalLocation":
                    return new LocalPoint(64 + 30 * 128, 64, WorldView.TOPLEVEL);
                default:
                    throw new AssertionError(method);
            }
        }));

        tracker.update(client, null);
        assertSame(Port.PORT_ROBERTS, tracker.getDock());

        atSea = false;
        tracker.update(client, null);
        assertSame(Port.PORT_ROBERTS, tracker.getDock());
    }

    @Test
    public void disembarkingAtDestinationUpdatesDockBeforeAtSeaFlagClears() {
        location = Port.PORT_ROBERTS.navigationLocation;
        tracker.update(client, Port.PORT_ROBERTS);

        location = Port.LANDS_END.navigationLocation;
        aboard = false;
        atSea = true;
        tracker.add(ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return Port.LANDS_END.gangplankObject;
                case "getWorldView":
                    return world;
                case "getLocalLocation":
                    return new LocalPoint(64, 64, WorldView.TOPLEVEL);
                default:
                    throw new AssertionError(method);
            }
        }));

        tracker.update(client, null);

        assertSame(Port.LANDS_END, tracker.getDock());
        assertSame(Port.LANDS_END, tracker.getStart());
    }
}
