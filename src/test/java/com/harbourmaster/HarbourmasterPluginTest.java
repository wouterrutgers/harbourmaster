package com.harbourmaster;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.data.BoatSize;
import com.harbourmaster.data.PortGraph;
import com.harbourmaster.data.PortTaskCatalog;
import com.harbourmaster.data.SailingRouter;
import com.harbourmaster.data.SailingSearch;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.RouteEvent;
import com.harbourmaster.model.RouteLeg;
import com.harbourmaster.optimizer.CourierCyclePlanner;
import com.harbourmaster.optimizer.RouteOptimizer;
import com.harbourmaster.tracker.OfferCycleTracker;
import com.harbourmaster.tracker.RouteTracker;
import com.harbourmaster.tracker.TaskVarbits;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
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
    private int routeSearches;
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
    private final Client client = ApiStub.of(Client.class, (method, arguments) -> {
        switch (method) {
            case "getGameState":
                return GameState.LOGGED_IN;
            case "getWidget":
            case "getItemContainer":
                return null;
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
        PortGraph graph = new PortGraph(new SailingRouter() {
            @Override
            public Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize size) {
                throw new AssertionError("Use incremental searches");
            }

            @Override
            public SailingSearch search(WorldPoint from, WorldPoint to, BoatSize size) {
                routeSearches++;
                return new SailingSearch() {
                    private int advances;

                    @Override
                    public boolean advance(int maximumExpandedStates) {
                        return ++advances >= 2;
                    }

                    @Override
                    public int expandedStates() {
                        return advances;
                    }

                    @Override
                    public Optional<RouteLeg> result() {
                        if (from.equals(blockedPosition)) {
                            return Optional.empty();
                        }
                        return Optional.of(new RouteLeg(
                                null, null, from.distanceTo(to), from.equals(to) ? List.of(from) : List.of(from, to)));
                    }
                };
            }
        });
        RouteOptimizer optimizer = new RouteOptimizer(graph);
        PortTaskCatalog catalog = new PortTaskCatalog();
        field(catalog, "loaded").set(catalog, true);
        field(catalog, "byId").set(catalog, Map.of(task.id, task));
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
