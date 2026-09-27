package com.harbourmaster;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.data.PortTaskCatalog;
import com.harbourmaster.model.CourierTask;
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
    private boolean boardOpen;
    private boolean detailsOpen;
    private Item carried;
    private WorldPoint stalledPosition;
    private final CountDownLatch searchStarted = new CountDownLatch(1);
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
            case "getItemContainer":
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
                    new CountDownLatch(1).await();
                    throw new AssertionError("Obsolete search must be cancelled");
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
        field(plugin, "config").set(plugin, new HarbourmasterConfig() {});
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
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(0, List.of(task));
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
    public void offerRouteRecoversAfterAnUnreachableBoatPosition()
            throws ReflectiveOperationException, InterruptedException {
        plugin.getPorts().update(client, A);
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(0, List.of(task));
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
    public void rememberedOffersMustBeAcceptedBeforeTheEighthCompletion()
            throws ReflectiveOperationException, InterruptedException {
        CourierTask held = courier(2, A, B, 100);
        PortTaskCatalog catalog = (PortTaskCatalog) field(plugin, "catalog").get(plugin);
        field(catalog, "byId").set(catalog, Map.of(task.id, task, held.id, held));
        plugin.getPorts().update(client, A);
        varbits.put(TaskVarbits.IDS[0], held.id);
        varbits.put(TaskVarbits.TAKEN[0], held.quantity);
        varbits.put(VarbitID.PORT_TASKS_COMPLETED_TODAY, 6);
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(6, List.of(task));
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
        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(7, List.of(task, betterOffer));
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

        ((OfferCycleTracker) field(plugin, "offerCycles").get(plugin)).observe(7, List.of(task));
        plugin.onGameTick(new GameTick());
        publishPlan();
        assertTrue(plugin.getSnapshot().dock.hasAcceptance());
        assertFalse(plugin.getSnapshot().dock.hasUnload());
        assertTrue(plugin.shouldUnloadCargo());

        carried = new Item(delivery.itemId, 1);
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldUnloadCargo());

        carried = null;
        aboard = false;
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.shouldUnloadCargo());
        assertTrue(plugin.getSnapshot().dock.hasAcceptance());
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

        WorldPoint firstPosition = new WorldPoint(destination.getX() - 20, destination.getY() + 2, 0);
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
        varbits.put(TaskVarbits.IDS[0], 0);
        tickAt(160);
        assertTrue(plugin.isGuidanceActive());
        tickAt(161);
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
