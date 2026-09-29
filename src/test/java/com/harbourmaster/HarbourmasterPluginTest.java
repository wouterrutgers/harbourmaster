package com.harbourmaster;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.data.BoatSize;
import com.harbourmaster.data.PortGraph;
import com.harbourmaster.data.PortTaskCatalog;
import com.harbourmaster.data.SailingRouteCache;
import com.harbourmaster.model.BoatFocus;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.DockGuidance;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.optimizer.CourierCyclePlanner;
import com.harbourmaster.optimizer.RouteOptimizer;
import com.harbourmaster.tracker.OfferCycleTracker;
import com.harbourmaster.tracker.RouteTracker;
import com.harbourmaster.tracker.TaskVarbits;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HarbourmasterPluginTest {
    private final HarbourmasterPlugin plugin = new HarbourmasterPlugin();
    private final ExecutorService planner = Executors.newSingleThreadExecutor();
    private final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
    private final Map<Integer, Integer> varbits = new HashMap<>();
    private final CourierTask task = new CourierTask(1, 1, "Observed offer", 1, B, 101, "Cargo", 3, 1000, B, D);
    private WorldPoint location = A.navigationLocation;
    private WorldPoint blockedPosition;
    private boolean aboard;
    private boolean useTeleports;
    private boolean aldarinTeleport;
    private boolean boardOpen;
    private boolean detailsOpen;
    private Item carried;
    private WorldPoint stalledPosition;
    private final CountDownLatch searchStarted = new CountDownLatch(1);
    private final CountDownLatch searchReleased = new CountDownLatch(1);
    private final CountDownLatch searchCancelled = new CountDownLatch(1);
    private int routeSearches;
    private final Widget boardEntry = ApiStub.of(Widget.class, (method, arguments) -> {
        if (method.equals("getOnOpListener")) {
            return new Object[] {1, 2, 3, task.databaseRow};
        }
        throw new AssertionError(method);
    });
    private final Widget board = ApiStub.of(Widget.class, (method, arguments) -> {
        switch (method) {
            case "isHidden":
                return !boardOpen;
            case "getDynamicChildren":
                return new Widget[] {boardEntry};
            default:
                throw new AssertionError(method);
        }
    });
    private final Widget details = ApiStub.of(Widget.class, (method, arguments) -> {
        if (method.equals("isHidden")) {
            return !detailsOpen;
        }
        throw new AssertionError(method);
    });
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
    private final WorldEntity entity = ApiStub.of(WorldEntity.class, (method, arguments) -> {
        switch (method) {
            case "getLocalLocation":
            case "transformToMainWorld":
                return new LocalPoint(64, 64, WorldView.TOPLEVEL);
            default:
                throw new AssertionError(method);
        }
    });
    private final IndexedObjectSet<WorldEntity> entities = new IndexedObjectSet<WorldEntity>() {
        @Override
        public WorldEntity byIndex(int index) {
            return entity;
        }

        @Override
        public Iterator<WorldEntity> iterator() {
            return List.of(entity).iterator();
        }
    };
    private final WorldView world = ApiStub.of(WorldView.class, (method, arguments) -> {
        switch (method) {
            case "isTopLevel":
                return true;
            case "isInstance":
                return false;
            case "getPlane":
                return 0;
            case "getBaseX":
                return location.getX();
            case "getBaseY":
                return location.getY();
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
    private final ItemContainer equipment = ApiStub.of(ItemContainer.class, (method, arguments) -> {
        if (method.equals("getItems")) {
            return carried == null ? new Item[0] : new Item[] {carried};
        }
        if (method.equals("getItem")) {
            assertEquals(EquipmentInventorySlot.WEAPON.getSlotIdx(), arguments[0]);
            return carried;
        }
        throw new AssertionError(method);
    });
    private final Client client = ApiStub.of(Client.class, (method, arguments) -> {
        switch (method) {
            case "getGameState":
                return GameState.LOGGED_IN;
            case "getWidget":
                if (arguments[0].equals(InterfaceID.PortTaskBoard.CONTAINER)) {
                    return board;
                }
                return arguments[0].equals(InterfaceID.PortTaskInfo.WINDOW) ? details : null;
            case "getDBTableField":
                return new Object[] {
                    Port.fromDatabaseRow((Integer) arguments[0]).ordinal()
                };
            case "getItemContainer":
                if (arguments[0].equals(InventoryID.SAILING_BOAT_1_CARGOHOLD)) {
                    return ApiStub.of(ItemContainer.class, (operation, parameters) -> {
                        assertEquals("getItems", operation);
                        return new Item[0];
                    });
                }
                assertEquals(InventoryID.WORN, arguments[0]);
                return equipment;
            case "getVarbitValue":
                return varbits.getOrDefault(arguments[0], 0);
            case "getVarpValue":
                return 0;
            case "getRealSkillLevel":
                return 99;
            case "getLocalPlayer":
                return player;
            case "getWorldView":
            case "getTopLevelWorldView":
                return world;
            default:
                throw new AssertionError(method);
        }
    });

    @Before
    public void initialize() throws ReflectiveOperationException {
        PortGraph graph = new PortGraph((from, to, size) -> {
            routeSearches++;
            if (from.equals(stalledPosition)) {
                searchStarted.countDown();
                try {
                    searchReleased.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    searchCancelled.countDown();
                    throw new CancellationException();
                }
            }
            if (from.equals(blockedPosition)) {
                return Optional.empty();
            }
            return Optional.of(
                    new RouteLeg(null, null, from.distanceTo(to), from.equals(to) ? List.of(from) : List.of(from, to)));
        });
        RouteOptimizer optimizer = new RouteOptimizer(graph);
        PortTaskCatalog catalog = new PortTaskCatalog();
        field(catalog, "loaded").set(catalog, true);
        field(catalog, "byId").set(catalog, Map.of(task.id, task));
        field(catalog, "byRow").set(catalog, Map.of(task.databaseRow, task));
        field(plugin, "client").set(plugin, client);
        field(plugin, "catalog").set(plugin, catalog);
        field(plugin, "config").set(plugin, new HarbourmasterConfig() {
            @Override
            public boolean useTeleports() {
                return useTeleports;
            }

            @Override
            public boolean aldarinTeleport() {
                return aldarinTeleport;
            }

            @Override
            public boolean summonBoat() {
                return true;
            }

            @Override
            public boolean teleportToBoat() {
                return true;
            }

            @Override
            public BoatFocus boat1Focus() {
                return BoatFocus.GREATER_TELEPORT_FOCUS;
            }
        });
        field(plugin, "running").set(plugin, true);
        field(plugin, "portGraph").set(plugin, graph);
        field(plugin, "routeTracker").set(plugin, new RouteTracker(optimizer));
        field(plugin, "cyclePlanner").set(plugin, new CourierCyclePlanner(optimizer));
        field(plugin, "plannerExecutor").set(plugin, planner);
        field(plugin, "clientThread").set(plugin, new ClientThread() {
            @Override
            public void invokeLater(Runnable callback) {
                callbacks.add(callback);
            }
        });
    }

    @After
    public void shutDown() {
        planner.shutdownNow();
    }

    @Test
    public void arrivingAboardKeepsDeliveryGuidanceWhenTheSavedBoatDockIsStale()
            throws ReflectiveOperationException, InterruptedException {
        useTeleports = true;
        aboard = true;
        CourierTask delivery = courier(1, A, B, 1000);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(delivery.id, delivery));
        varbits.put(TaskVarbits.IDS[0], delivery.id);
        varbits.put(TaskVarbits.TAKEN[0], delivery.quantity);
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SAILING_TRANSMIT_IS_AT_SEA, 1);
        location = new WorldPoint(B.navigationLocation.getX() - 30, B.navigationLocation.getY(), 0);
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertTrue(plugin.getSnapshot().sailingNext());

        dock();
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(DockGuidance.TAKE_DELIVERY_CARGO, plugin.getDockGuidance());

        varbits.put(VarbitID.SAILING_TRANSMIT_IS_AT_SEA, 0);
        plugin.onGameTick(new GameTick());

        assertNull(plugin.getSnapshot().currentLeg);
        assertTrue(plugin.getSnapshot().dock.hasUnload());
        assertEquals(delivery.quantity, plugin.getSnapshot().dock.actions.get(0).quantity);
        assertTrue(plugin.shouldUnloadCargo());
        assertEquals(DockGuidance.TAKE_DELIVERY_CARGO, plugin.getDockGuidance());

        carried = new Item(delivery.itemId, 1);
        assertEquals(DockGuidance.LEAVE_TO_DELIVER, plugin.getDockGuidance());

        aboard = false;
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertNull(plugin.getSnapshot().currentLeg);
        assertTrue(plugin.getSnapshot().dock.hasUnload());
        assertEquals(DockGuidance.DELIVER, plugin.getDockGuidance());
    }

    @Test
    public void settingsChangingDuringPlanningDiscardTheObsoleteTeleportRoute()
            throws ReflectiveOperationException, InterruptedException {
        dock();
        useTeleports = true;
        varbits.put(VarbitID.SAILING_INTRO, 50);
        CourierTask delivery = courier(1, Port.ALDARIN, D, 1000);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(delivery.id, delivery));
        varbits.put(TaskVarbits.IDS[0], delivery.id);
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, B.ordinal());
        varbits.put(VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, 1);
        varbits.put(VarbitID.VARLAMORE_VISITED, 1);
        aldarinTeleport = true;
        plugin.onGameTick(new GameTick());
        Runnable obsolete = callbacks.poll(5, TimeUnit.SECONDS);
        assertNotNull(obsolete);

        aldarinTeleport = false;
        plugin.onGameTick(new GameTick());
        obsolete.run();
        assertTrue(plugin.getSnapshot().route.legs.isEmpty());
        publishPlan();
        assertTrue(plugin.getSnapshot().route.available);
        assertTrue(plugin.getSnapshot().route.legs.stream().allMatch(RouteLeg::sailingOnly));
    }

    @Test
    public void unknownStartingPortPublishesAnUnavailableRoute() throws InterruptedException {
        location = new WorldPoint(3500, 3500, 0);
        varbits.put(TaskVarbits.IDS[0], task.id);

        plugin.onGameTick(new GameTick());
        publishPlan();

        assertTrue(plugin.getSnapshot().loggedIn);
        assertFalse(plugin.getSnapshot().route.available);
        assertFalse(plugin.isCalculatingPlan());
    }

    @Test
    public void rememberedOfferRouteSurvivesMovementAndUpdatesWhenTheSearchFinishes()
            throws ReflectiveOperationException, InterruptedException {
        plugin.getPorts().update(client, A);
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(0, task.board, List.of(task));
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(2, plugin.getSnapshot().route.stops.size());

        aboard = true;
        location = new WorldPoint(3000, 3100, 0);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.getSnapshot().route.available);
        assertEquals(2, plugin.getSnapshot().route.stops.size());

        publishPlan();
        assertEquals(location, plugin.getSnapshot().route.legs.get(0).points.get(0));
        assertEquals(List.of(task), plugin.getSnapshot().courierPlan.selectedOffers);
    }

    @Test
    public void leavingTheDockKeepsSailingGuidanceWhileTravelOptionsAreRecalculated()
            throws ReflectiveOperationException, InterruptedException {
        useTeleports = true;
        aboard = true;
        dock();
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, B.ordinal());
        varbits.put(TaskVarbits.IDS[0], task.id);
        varbits.put(TaskVarbits.TAKEN[0], task.quantity);
        CourierTask offer = new CourierTask(2, 2, "Next offer", 1, D, 102, "Cargo", 1, 10000, D, E);
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(0, D, List.of(offer));
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(List.of(offer), plugin.getSnapshot().courierPlan.selectedOffers);
        assertEquals(D, plugin.getSnapshot().nextPort());

        varbits.put(VarbitID.SAILING_TRANSMIT_IS_AT_SEA, 1);
        location = new WorldPoint(B.navigationLocation.getX() + 15, B.navigationLocation.getY(), 0);
        stalledPosition = location;
        plugin.onGameTick(new GameTick());
        assertTrue(searchStarted.await(5, TimeUnit.SECONDS));

        location = new WorldPoint(location.getX() + 4, location.getY(), 0);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.isCalculatingPlan());
        assertTrue(plugin.getSnapshot().sailingNext());
        assertFalse(plugin.getSnapshot().navigation.isEmpty());
        assertEquals(D, plugin.getSnapshot().nextPort());
        assertEquals(List.of(offer), plugin.getSnapshot().courierPlan.selectedOffers);

        searchReleased.countDown();
        publishPlan();
        publishPlan();
        assertFalse(plugin.isCalculatingPlan());
        assertEquals(D, plugin.getSnapshot().nextPort());
        assertEquals(location, plugin.getSnapshot().currentLeg.points.get(0));
        assertEquals(List.of(offer), plugin.getSnapshot().courierPlan.selectedOffers);
    }

    @Test
    public void offerRouteRecoversAfterAnUnreachableBoatPosition()
            throws ReflectiveOperationException, InterruptedException {
        plugin.getPorts().update(client, A);
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(0, task.board, List.of(task));
        plugin.onGameTick(new GameTick());
        publishPlan();

        aboard = true;
        blockedPosition = new WorldPoint(3000, 3100, 0);
        location = blockedPosition;
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertFalse(plugin.getSnapshot().route.available);

        location = new WorldPoint(3001, 3100, 0);
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertTrue(plugin.getSnapshot().route.available);
        assertEquals(2, plugin.getSnapshot().route.stops.size());
        assertEquals(location, plugin.getSnapshot().currentLeg.points.get(0));
        assertEquals(List.of(task), plugin.getSnapshot().courierPlan.selectedOffers);
    }

    @Test
    public void unavailableTravelPlanRetriesWhenTheBoatMoves() throws InterruptedException {
        useTeleports = true;
        aboard = true;
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_OWNED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_PORT, A.ordinal());
        varbits.put(VarbitID.SAILING_TRANSMIT_IS_AT_SEA, 1);
        varbits.put(TaskVarbits.IDS[0], task.id);
        varbits.put(TaskVarbits.TAKEN[0], task.quantity);
        plugin.getPorts().update(client, A);
        blockedPosition = new WorldPoint(3000, 3100, 0);
        location = blockedPosition;
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertFalse(plugin.getSnapshot().route.available);
        assertFalse(plugin.isCalculatingPlan());
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.isCalculatingPlan());

        location = new WorldPoint(3001, 3100, 0);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.isCalculatingPlan());
        publishPlan();

        assertTrue(plugin.getSnapshot().route.available);
        assertEquals(
                location, plugin.getSnapshot().currentLeg.steps.get(0).points.get(0));
        assertEquals(D, plugin.getSnapshot().nextPort());
        assertFalse(plugin.isCalculatingPlan());
    }

    @Test
    public void completingATaskReconsidersRepeatOffersBeforeTheRemainingBundleIsAccepted()
            throws ReflectiveOperationException, InterruptedException {
        CourierTask seeds = new CourierTask(
                377,
                9040,
                "Lunar Isle crystal seed delivery",
                76,
                Port.PRIFDDINAS,
                32581,
                "Crystal seeds",
                8,
                2746,
                Port.PRIFDDINAS,
                Port.LUNAR_ISLE);
        CourierTask hides = new CourierTask(
                428,
                9091,
                "Deepfin Point suqah hide delivery",
                76,
                Port.LUNAR_ISLE,
                32545,
                "Suqah hides",
                8,
                4636,
                Port.LUNAR_ISLE,
                Port.DEEPFIN_POINT);
        CourierTask fur = new CourierTask(
                433,
                9096,
                "Lunar Isle fur delivery",
                76,
                Port.LUNAR_ISLE,
                32578,
                "Fur",
                7,
                9337,
                Port.CIVITAS_ILLA_FORTIS,
                Port.LUNAR_ISLE);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(seeds.id, seeds, hides.id, hides, fur.id, fur));
        SailingRouteCache.load((PortGraph) field(plugin, "portGraph").get(plugin), BoatSize.SLOOP);
        varbits.put(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED, 1);
        varbits.put(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, 1);
        varbits.put(VarbitID.SAILING_BOAT_1_KEEL, 4);
        OfferCycleTracker offers =
                (OfferCycleTracker) field(plugin, "offerCycles").get(plugin);
        offers.observe(0, Port.PRIFDDINAS, List.of(seeds));
        offers.observe(0, Port.LUNAR_ISLE, List.of(hides, fur));
        location = Port.DEEPFIN_POINT.navigationLocation;
        plugin.getPorts().update(client, Port.DEEPFIN_POINT);
        plugin.onGameTick(new GameTick());
        publishPlan();

        location = Port.PRIFDDINAS.navigationLocation;
        plugin.getPorts().update(client, Port.PRIFDDINAS);
        varbits.put(TaskVarbits.IDS[0], seeds.id);
        varbits.put(TaskVarbits.TAKEN[0], seeds.quantity);
        plugin.onGameTick(new GameTick());
        publishPlan();

        location = Port.LUNAR_ISLE.navigationLocation;
        plugin.getPorts().update(client, Port.LUNAR_ISLE);
        varbits.put(TaskVarbits.IDS[1], hides.id);
        varbits.put(TaskVarbits.TAKEN[1], hides.quantity);
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(List.of(fur), plugin.getSnapshot().courierPlan.selectedOffers);

        varbits.remove(TaskVarbits.IDS[0]);
        varbits.remove(TaskVarbits.TAKEN[0]);
        varbits.put(VarbitID.PORT_TASKS_COMPLETED_TODAY, 1);
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertTrue(plugin.getSnapshot().courierPlan.selectedOffers.contains(seeds));
    }

    @Test
    public void rememberedOffersMustBeAcceptedBeforeTheEighthCompletion()
            throws ReflectiveOperationException, InterruptedException {
        CourierTask held = courier(2, A, B, 100);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(task.id, task, held.id, held));
        plugin.getPorts().update(client, A);
        varbits.put(TaskVarbits.IDS[0], held.id);
        varbits.put(TaskVarbits.TAKEN[0], held.quantity);
        varbits.put(VarbitID.PORT_TASKS_COMPLETED_TODAY, 6);
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(6, task.board, List.of(task));
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(List.of(task), plugin.getSnapshot().courierPlan.selectedOffers);

        varbits.put(VarbitID.PORT_TASKS_COMPLETED_TODAY, 7);
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertTrue(plugin.getSnapshot().courierPlan.selectedOffers.isEmpty());

        varbits.put(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED, 1);
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(List.of(task), plugin.getSnapshot().courierPlan.selectedOffers);
        assertEquals(
                RouteEvent.Action.ACCEPT,
                plugin.getSnapshot().route.stops.get(0).events.get(0).action);

        location = B.navigationLocation;
        plugin.getPorts().add(ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return B.ledgerObject;
                case "getWorldView":
                    return world;
                case "getLocalLocation":
                    return new LocalPoint(64, 64, WorldView.TOPLEVEL);
                default:
                    throw new AssertionError(method);
            }
        }));
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertTrue(plugin.getSnapshot().recommends(task));
        assertTrue(plugin.getSnapshot().dock.hasAcceptance());
        assertFalse(plugin.getSnapshot().dock.hasUnload());

        CourierTask betterOffer = new CourierTask(3, 3, "Better offer", 1, B, 103, "Cargo", 3, 10000, B, D);
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin))
                .observe(7, task.board, List.of(task, betterOffer));
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertEquals(List.of(betterOffer), plugin.getSnapshot().courierPlan.selectedOffers);
    }

    @Test
    public void boatCargoIsUnloadedBeforeCheckingOrAcceptingOffers()
            throws ReflectiveOperationException, InterruptedException {
        CourierTask delivery = courier(2, A, B, 1);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(task.id, task, delivery.id, delivery));
        dock();
        aboard = true;
        varbits.put(TaskVarbits.IDS[0], delivery.id);
        varbits.put(TaskVarbits.TAKEN[0], delivery.quantity);
        varbits.put(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED, 1);
        varbits.put(VarbitID.PORT_TASKS_COMPLETED_TODAY, 7);
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertTrue(plugin.shouldUnloadCargo());
        assertFalse(plugin.shouldCheckNoticeboard());

        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(7, task.board, List.of(task));
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertTrue(plugin.getSnapshot().dock.hasAcceptance());
        assertFalse(plugin.getSnapshot().dock.hasUnload());
        assertTrue(plugin.shouldUnloadCargo());

        carried = new Item(delivery.itemId, 1);
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldUnloadCargo());
        assertEquals(DockGuidance.LEAVE_TO_DELIVER, plugin.getDockGuidance());

        aboard = false;
        plugin.onGameTick(new GameTick());
        assertEquals(DockGuidance.DELIVER, plugin.getDockGuidance());

        carried = null;
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldUnloadCargo());
        assertTrue(plugin.getSnapshot().dock.hasAcceptance());
        assertEquals(DockGuidance.CHECK_BOARD, plugin.getDockGuidance());
    }

    @Test
    public void completedTasksDoNotInterruptFetchingTheRemainingDeliveryCrates()
            throws ReflectiveOperationException, InterruptedException {
        CourierTask first = courier(2, A, B, 1000);
        CourierTask remaining = courier(3, A, B, 1000);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(first.id, first, remaining.id, remaining));
        dock();
        varbits.put(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED, 1);
        varbits.put(TaskVarbits.IDS[0], first.id);
        varbits.put(TaskVarbits.TAKEN[0], first.quantity);
        varbits.put(TaskVarbits.DELIVERED[0], first.quantity);
        varbits.put(TaskVarbits.IDS[1], remaining.id);
        varbits.put(TaskVarbits.TAKEN[1], remaining.quantity);
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertEquals(DockGuidance.BOARD_TO_FETCH, plugin.getDockGuidance());
    }

    @Test
    public void completedPickupStopsShowingTheOldLedgerActionWhileTheNextRouteIsCalculated()
            throws InterruptedException {
        dock();
        varbits.put(TaskVarbits.IDS[0], task.id);
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(DockGuidance.PICKUP, plugin.getDockGuidance());

        varbits.put(TaskVarbits.TAKEN[0], task.quantity);
        plugin.onGameTick(new GameTick());

        assertTrue(plugin.isCalculatingPlan());
        assertNotEquals(DockGuidance.PICKUP, plugin.getDockGuidance());

        publishPlan();
        assertEquals(D, plugin.getSnapshot().nextPort());
        assertEquals(DockGuidance.BOARD_TO_SAIL, plugin.getDockGuidance());

        aboard = true;
        varbits.put(VarbitID.SAILING_TRANSMIT_IS_AT_SEA, 1);
        plugin.onGameTick(new GameTick());
        assertEquals(B, plugin.getSnapshot().dock.port);
        assertEquals(DockGuidance.NONE, plugin.getDockGuidance());
    }

    @Test
    public void sailingUpdatesTheDisplayedRouteWhileTheBoatKeepsMoving() throws InterruptedException {
        plugin.getPorts().update(client, A);
        aboard = true;
        WorldPoint destination = B.navigationLocation;
        location = new WorldPoint(destination.getX() - 35, destination.getY(), 0);
        varbits.put(TaskVarbits.IDS[0], task.id);
        plugin.onGameTick(new GameTick());
        location = new WorldPoint(destination.getX() - 30, destination.getY(), 0);
        publishPlan();
        assertEquals(30, plugin.getSnapshot().currentLeg.distance, 0);

        WorldPoint firstPosition = new WorldPoint(destination.getX() - 20, destination.getY() + 15, 0);
        location = firstPosition;
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.isCalculatingPlan());
        WorldPoint secondPosition = new WorldPoint(destination.getX() - 10, destination.getY() + 2, 0);
        location = secondPosition;
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertEquals(firstPosition, plugin.getSnapshot().currentLeg.points.get(0));
        assertTrue(plugin.getSnapshot().currentLeg.distance < 30);

        publishPlan();
        assertEquals(secondPosition, plugin.getSnapshot().currentLeg.points.get(0));
        assertTrue(plugin.getSnapshot().currentLeg.distance < 20);
        int searchesBeforeFollowingRoute = routeSearches;
        location = new WorldPoint(destination.getX() - 5, destination.getY() + 1, 0);
        plugin.onGameTick(new GameTick());
        assertEquals(location, plugin.getSnapshot().currentLeg.points.get(0));
        assertEquals(location, plugin.getSnapshot().navigation.get(0).points.get(0).tile);
        assertEquals(Math.hypot(5, 1), plugin.getSnapshot().currentLeg.distance, 0.00001);
        assertEquals(searchesBeforeFollowingRoute, routeSearches);
    }

    @Test
    public void returningToPortCancelsThePreviousSailingCalculation() throws InterruptedException {
        plugin.getPorts().update(client, B);
        aboard = true;
        location = new WorldPoint(3000, 3100, 0);
        stalledPosition = location;
        varbits.put(TaskVarbits.IDS[0], task.id);
        plugin.onGameTick(new GameTick());
        assertTrue(searchStarted.await(5, TimeUnit.SECONDS));

        aboard = false;
        location = B.navigationLocation;
        plugin.getPorts().update(client, B);
        plugin.onGameTick(new GameTick());
        assertTrue(searchCancelled.await(5, TimeUnit.SECONDS));
        publishPlan();

        assertTrue(plugin.getSnapshot().route.available);
        assertFalse(plugin.isCalculatingPlan());
        assertEquals(B, plugin.getSnapshot().route.stops.get(0).port);
        assertTrue(callbacks.isEmpty());
    }

    @Test
    public void unreadDockOffersPromptABoardCheckBeforeContinuingTheRoute()
            throws ReflectiveOperationException, InterruptedException {
        CourierTask delivery = courier(2, A, D, 100);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(task.id, task, delivery.id, delivery));
        varbits.put(TaskVarbits.IDS[0], delivery.id);
        varbits.put(TaskVarbits.TAKEN[0], delivery.quantity);
        varbits.put(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED, 1);
        dock();
        aboard = true;
        plugin.onGameTick(new GameTick());
        publishPlan();

        assertEquals(D, plugin.getSnapshot().nextPort());
        assertTrue(plugin.shouldCheckNoticeboard());

        boardOpen = true;
        plugin.onGameTick(new GameTick());
        publishPlan();
        boardOpen = false;
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldCheckNoticeboard());
        assertTrue(plugin.getSnapshot().dock.hasAcceptance());

        varbits.put(VarbitID.PORT_TASKS_COMPLETED_TODAY, 8);
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertTrue(plugin.shouldCheckNoticeboard());

        varbits.put(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED, 0);
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldCheckNoticeboard());
    }

    @Test
    public void guidanceRequiresABoatOrDockAndExpiresAfterTheLastTask() throws ReflectiveOperationException {
        dock();
        tickAt(0);
        assertFalse(plugin.isGuidanceActive());

        varbits.put(TaskVarbits.IDS[0], task.id);
        tickAt(1);
        assertTrue(plugin.isGuidanceActive());

        plugin.getPorts().clear();
        tickAt(2);
        assertFalse(plugin.isGuidanceActive());

        aboard = true;
        tickAt(100);
        assertTrue(plugin.isGuidanceActive());

        varbits.put(TaskVarbits.TAKEN[0], task.quantity);
        varbits.put(TaskVarbits.DELIVERED[0], task.quantity);
        tickAt(101);
        assertTrue(plugin.isGuidanceActive());
        dock();
        aboard = false;
        tickAt(1000);
        assertTrue(plugin.isGuidanceActive());
        assertEquals(DockGuidance.CLAIM_REWARDS, plugin.getDockGuidance());
        varbits.put(TaskVarbits.IDS[0], 0);
        tickAt(1001);
        tickAt(1060);
        assertTrue(plugin.isGuidanceActive());
        tickAt(1061);
        assertFalse(plugin.isGuidanceActive());
    }

    @Test
    public void browsingANoticeboardActivatesGuidanceThroughDetailsAndUntilAMinuteAfterClosing()
            throws ReflectiveOperationException {
        dock();
        tickAt(0);
        assertFalse(plugin.isGuidanceActive());

        boardOpen = true;
        tickAt(10);
        assertTrue(plugin.isGuidanceActive());
        plugin.getNoticeboard().beginOpeningDetails(boardEntry);
        boardOpen = false;
        tickAt(70);
        assertTrue(plugin.isGuidanceActive());
        detailsOpen = true;
        tickAt(130);
        assertTrue(plugin.isGuidanceActive());

        detailsOpen = false;
        tickAt(131);
        tickAt(190);
        assertTrue(plugin.isGuidanceActive());
        tickAt(191);
        assertFalse(plugin.isGuidanceActive());

        boardOpen = true;
        tickAt(200);
        boardOpen = false;
        varbits.put(TaskVarbits.IDS[0], task.id);
        tickAt(201);
        tickAt(261);
        assertTrue(plugin.isGuidanceActive());
    }

    @Test
    public void changingWorldClearsTheGuidanceGracePeriod() throws ReflectiveOperationException {
        aboard = true;
        varbits.put(TaskVarbits.IDS[0], task.id);
        tickAt(0);
        varbits.put(TaskVarbits.IDS[0], 0);
        tickAt(1);
        assertTrue(plugin.isGuidanceActive());

        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.HOPPING);
        plugin.onGameStateChanged(event);
        assertFalse(plugin.isGuidanceActive());
        tickAt(2);
        assertFalse(plugin.isGuidanceActive());
    }

    private void tickAt(long seconds) throws ReflectiveOperationException {
        field(plugin, "clock").set(plugin, Clock.fixed(Instant.EPOCH.plusSeconds(seconds), ZoneOffset.UTC));
        plugin.onGameTick(new GameTick());
    }

    private void dock() {
        location = B.navigationLocation;
        plugin.getPorts().add(ApiStub.of(GameObject.class, (method, arguments) -> {
            switch (method) {
                case "getId":
                    return B.noticeboardObject;
                case "getWorldView":
                    return world;
                case "getLocalLocation":
                    return new LocalPoint(64, 64, WorldView.TOPLEVEL);
                default:
                    throw new AssertionError(method);
            }
        }));
    }

    private void publishPlan() throws InterruptedException {
        Runnable callback = callbacks.poll(5, TimeUnit.SECONDS);
        assertNotNull(callback);
        callback.run();
    }

    private static Field field(Object object, String name) throws NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
