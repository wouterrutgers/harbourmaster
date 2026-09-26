package com.harbourmaster;

import com.google.inject.Provides;
import com.harbourmaster.data.BoatSize;
import com.harbourmaster.data.PortGraph;
import com.harbourmaster.data.PortTaskCatalog;
import com.harbourmaster.data.SailingPathfinder;
import com.harbourmaster.data.SailingRouteCache;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierPlan;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.DockChecklist;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.optimizer.CourierCyclePlanner;
import com.harbourmaster.optimizer.RouteOptimizer;
import com.harbourmaster.overlay.CargoOverlay;
import com.harbourmaster.overlay.DockOverlay;
import com.harbourmaster.overlay.MinimapRouteOverlay;
import com.harbourmaster.overlay.NavigationOverlay;
import com.harbourmaster.overlay.NoticeboardOverlay;
import com.harbourmaster.overlay.RouteStatusOverlay;
import com.harbourmaster.overlay.WorldMapRouteOverlay;
import com.harbourmaster.tracker.ActiveTaskTracker;
import com.harbourmaster.tracker.CargoTracker;
import com.harbourmaster.tracker.NoticeboardTracker;
import com.harbourmaster.tracker.OfferCycleTracker;
import com.harbourmaster.tracker.PortTracker;
import com.harbourmaster.tracker.RouteTracker;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexDataBase;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = "Harbourmaster",
        description = "Optimizes Sailing courier routes and suggests tasks, with cargo and dock guidance",
        tags = {"sailing", "port", "courier", "cargo", "tasks", "route", "optimizer", "navigation"})
public class HarbourmasterPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarbourmasterPlugin.class);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private HarbourmasterConfig config;

    @Inject
    private PortTaskCatalog catalog;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private NoticeboardOverlay noticeboardOverlay;

    @Inject
    private DockOverlay dockOverlay;

    @Inject
    private CargoOverlay cargoOverlay;

    @Inject
    private NavigationOverlay navigationOverlay;

    @Inject
    private MinimapRouteOverlay minimapOverlay;

    @Inject
    private WorldMapRouteOverlay worldMapOverlay;

    @Inject
    private RouteStatusOverlay statusOverlay;

    private final ActiveTaskTracker activeTasks = new ActiveTaskTracker();
    private final NoticeboardTracker noticeboard = new NoticeboardTracker();
    private final PortTracker ports = new PortTracker();
    private final CargoTracker cargo = new CargoTracker();
    private PortGraph portGraph;
    private RouteTracker routeTracker;
    private CourierCyclePlanner cyclePlanner;
    private final OfferCycleTracker offerCycles = new OfferCycleTracker();
    private volatile HarbourmasterSnapshot snapshot = HarbourmasterSnapshot.empty();
    private volatile boolean running;
    private List<Object> previousInputs;
    private List<Object> previousPlanInputs;
    private CourierPlan previousCourierPlan;
    private ExecutorService plannerExecutor;
    private volatile boolean optimizerInitializing;
    private volatile CourierPlanRequest pendingPlanRequest;

    @Provides
    HarbourmasterConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(HarbourmasterConfig.class);
    }

    @Override
    protected void startUp() {
        running = true;
        plannerExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "harbourmaster-planner");
            thread.setDaemon(true);
            return thread;
        });
        for (Overlay overlay : overlays()) {
            overlayManager.add(overlay);
        }
        clientThread.invokeLater(() -> {
            if (running && client.getGameState() == GameState.LOGGED_IN) {
                ports.scanScene(client);
                refresh();
            }
        });
    }

    @Override
    protected void shutDown() {
        running = false;
        for (Overlay overlay : overlays()) {
            overlayManager.remove(overlay);
        }
        plannerExecutor.shutdownNow();
        clientThread.invokeLater(this::clear);
    }

    private List<Overlay> overlays() {
        return List.of(
                noticeboardOverlay,
                dockOverlay,
                cargoOverlay,
                navigationOverlay,
                minimapOverlay,
                worldMapOverlay,
                statusOverlay);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (running) {
            refresh();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        Widget details = client.getWidget(InterfaceID.PortTaskInfo.WINDOW);
        if (noticeboard.isOpen() && (details == null || details.isHidden())) {
            noticeboard.beginOpeningDetails(event.getWidget());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            clear();
        } else if (event.getGameState() == GameState.LOGGED_IN) {
            ports.scanScene(client);
            initializeOptimizer();
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        ports.add(event.getGameObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        ports.remove(event.getGameObject());
    }

    @Subscribe
    public void onWorldViewUnloaded(WorldViewUnloaded event) {
        ports.unload(event.getWorldView());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"harbourmaster".equals(event.getGroup())) {
            return;
        }
        clientThread.invokeLater(() -> {
            previousInputs = null;
            if (running) {
                refresh();
            }
        });
    }

    private void clear() {
        ports.clear();
        noticeboard.clear();
        offerCycles.clear();
        clearPlan();
        previousInputs = null;
        snapshot = HarbourmasterSnapshot.empty();
    }

    private void clearPlan() {
        pendingPlanRequest = null;
        if (routeTracker != null) {
            portGraph.clearPendingRouteSearches();
            routeTracker.clear();
        }
        previousPlanInputs = null;
        previousCourierPlan = null;
    }

    private void refresh() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (portGraph == null) {
            initializeOptimizer();
            return;
        }
        if (!catalog.isLoaded()) {
            catalog.load(client);
        }
        noticeboard.scan(client, catalog);
        ports.update(client, noticeboard.isOpen() ? noticeboard.getPort() : null);
        if (portGraph.setBoatSize(ports.getBoatSize())) {
            clearPlan();
        }
        List<ActiveTask> held = activeTasks.read(
                client::getVarbitValue, client::getVarpValue, catalog::byId, catalog::isIgnoredTask, ports.getStart());
        int completedTasks = client.getVarbitValue(VarbitID.PORT_TASKS_COMPLETED_TODAY);
        offerCycles.observe(completedTasks, noticeboard.isOpen() ? noticeboard.getOffers() : List.of());
        if (noticeboard.isDetailsOpen() || noticeboard.isOpeningDetails()) {
            return;
        }
        Map<Integer, CargoTracker.Destination> destinations = cargo.destinations(held, ports.getDock());
        boolean depositCargo = cargo.needsDeposit(client, destinations, ports.getDock());
        int level = client.getRealSkillLevel(Skill.SAILING);
        int capacity = Math.min(5, 1 + client.getVarbitValue(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED));
        int freeSlots = Math.max(0, capacity - activeTasks.occupiedSlots(client::getVarbitValue));
        Map<Port, List<CourierTask>> observedOffers = offerCycles.offers();
        List<Object> inputs = Arrays.asList(
                held,
                noticeboard.getOffers(),
                noticeboard.getPort(),
                noticeboard.isOpen(),
                ports.getStart(),
                ports.getBoatPosition(),
                ports.getDock(),
                ports.getBoatSize(),
                level,
                freeSlots,
                depositCargo,
                config.enableOptimizer(),
                config.rankOffers(),
                completedTasks,
                observedOffers);
        if (inputs.equals(previousInputs)) {
            return;
        }
        previousInputs = inputs;
        RoutePlan route;
        CourierPlan courierPlan;
        if (config.enableOptimizer()) {
            courierPlan = updatePlan(held, observedOffers, level, freeSlots, offerCycles.tasksUntilReset());
            route = courierPlan == null ? RoutePlan.empty() : courierPlan.route;
        } else {
            clearPlan();
            route = RoutePlan.unavailable("Route optimiser disabled");
            courierPlan = null;
        }
        snapshot = new HarbourmasterSnapshot(
                true,
                route,
                noticeboard.getOffers(),
                noticeboard.isOpen(),
                freeSlots,
                DockChecklist.at(ports.getDock(), held),
                destinations,
                depositCargo,
                courierPlan);
    }

    private CourierPlan updatePlan(
            List<ActiveTask> held,
            Map<Port, List<CourierTask>> observedOffers,
            int level,
            int freeSlots,
            int tasksUntilReset) {
        List<Object> inputs = Arrays.asList(
                held,
                observedOffers,
                level,
                freeSlots,
                ports.getStart(),
                ports.getBoatSize(),
                config.rankOffers(),
                tasksUntilReset);
        if (!inputs.equals(previousPlanInputs)) {
            previousPlanInputs = inputs;
            requestPlan(held, observedOffers, level, freeSlots, tasksUntilReset, null);
        }
        if (previousCourierPlan == null || pendingPlanRequest != null) {
            return previousCourierPlan;
        }

        CourierPlan plan = previousCourierPlan.selectedOffers.isEmpty()
                ? cyclePlanner.withRoute(
                        previousCourierPlan, routeTracker.update(ports.getStart(), ports.getBoatPosition(), held))
                : cyclePlanner.relocate(previousCourierPlan, ports.getStart(), ports.getBoatPosition());
        if (portGraph.hasPendingRouteSearches()) {
            portGraph.clearPendingRouteSearches();
            requestPlan(
                    held,
                    observedOffers,
                    level,
                    freeSlots,
                    tasksUntilReset,
                    previousCourierPlan.available ? previousCourierPlan : null);
            return previousCourierPlan;
        }
        if (plan.available) {
            previousCourierPlan = plan;
        }
        return plan;
    }

    private void initializeOptimizer() {
        if (portGraph != null || optimizerInitializing) {
            return;
        }
        optimizerInitializing = true;
        plannerExecutor.execute(() -> {
            SailingPathfinder.prepareMasks();
            clientThread.invokeLater(() -> {
                optimizerInitializing = false;
                if (!running || client.getGameState() != GameState.LOGGED_IN) {
                    return;
                }
                IndexDataBase mapIndex = client.getIndex(SailingPathfinder.MAP_INDEX_ID);
                portGraph = new PortGraph(new SailingPathfinder(mapIndex, task -> clientThread.invokeLater(task)));
                for (BoatSize boatSize : BoatSize.values()) {
                    portGraph.loadPortRoutes(boatSize, SailingRouteCache.load(boatSize));
                }
                RouteOptimizer optimizer = new RouteOptimizer(portGraph);
                routeTracker = new RouteTracker(optimizer);
                cyclePlanner = new CourierCyclePlanner(optimizer);
                previousInputs = null;
                refresh();
            });
        });
    }

    private void requestPlan(
            List<ActiveTask> held,
            Map<Port, List<CourierTask>> observedOffers,
            int level,
            int freeSlots,
            int tasksUntilReset,
            CourierPlan routeToUpdate) {
        CourierPlanRequest request = new CourierPlanRequest(
                ports.getStart(),
                ports.getBoatPosition(),
                held,
                config.rankOffers() ? observedOffers : Map.of(),
                level,
                freeSlots,
                tasksUntilReset,
                routeToUpdate);
        pendingPlanRequest = request;
        PortGraph routeSnapshot = portGraph.detachedSnapshot(request.boatPosition);
        plannerExecutor.execute(() -> {
            try {
                CourierCyclePlanner planner = new CourierCyclePlanner(new RouteOptimizer(routeSnapshot));
                CourierPlan plan = request.routeToUpdate == null
                        ? planner.plan(
                                request.start,
                                request.boatPosition,
                                request.held,
                                request.observedOffers,
                                request.sailingLevel,
                                request.freeSlots,
                                request.tasksUntilReset)
                        : planner.relocate(request.routeToUpdate, request.start, request.boatPosition);
                clientThread.invokeLater(() -> publishPlan(request, routeSnapshot, plan));
            } catch (RuntimeException exception) {
                clientThread.invokeLater(() -> failPlan(request, exception));
            }
        });
    }

    private void publishPlan(CourierPlanRequest request, PortGraph routeSnapshot, CourierPlan plan) {
        if (!running || pendingPlanRequest != request) {
            return;
        }
        portGraph.mergeComputedRoutes(routeSnapshot);
        if (request.routeToUpdate == null) {
            if (plan.selectedOffers.isEmpty()) {
                routeTracker.update(request.start, request.boatPosition, request.held);
            }
            previousCourierPlan = plan;
        } else if (plan.available) {
            previousCourierPlan = plan;
        }
        pendingPlanRequest = null;
        previousInputs = null;
        refresh();
    }

    private void failPlan(CourierPlanRequest request, RuntimeException exception) {
        if (!running || pendingPlanRequest != request) {
            return;
        }
        LOGGER.error("Unable to calculate a courier plan", exception);
        pendingPlanRequest = null;
        previousInputs = null;
        refresh();
    }

    private static final class CourierPlanRequest {
        private final Port start;
        private final WorldPoint boatPosition;
        private final List<ActiveTask> held;
        private final Map<Port, List<CourierTask>> observedOffers;
        private final int sailingLevel;
        private final int freeSlots;
        private final int tasksUntilReset;
        private final CourierPlan routeToUpdate;

        private CourierPlanRequest(
                Port start,
                WorldPoint boatPosition,
                List<ActiveTask> held,
                Map<Port, List<CourierTask>> observedOffers,
                int sailingLevel,
                int freeSlots,
                int tasksUntilReset,
                CourierPlan routeToUpdate) {
            this.start = start;
            this.boatPosition = boatPosition;
            this.held = held;
            this.observedOffers = observedOffers;
            this.sailingLevel = sailingLevel;
            this.freeSlots = freeSlots;
            this.tasksUntilReset = tasksUntilReset;
            this.routeToUpdate = routeToUpdate;
        }
    }

    public HarbourmasterSnapshot getSnapshot() {
        return snapshot;
    }

    public boolean isCalculatingPlan() {
        return optimizerInitializing || pendingPlanRequest != null;
    }

    public NoticeboardTracker getNoticeboard() {
        return noticeboard;
    }

    public PortTracker getPorts() {
        return ports;
    }
}
