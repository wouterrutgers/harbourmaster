package com.harbourmaster;

import com.google.inject.Provides;
import com.harbourmaster.data.PortGraph;
import com.harbourmaster.data.PortPathData;
import com.harbourmaster.data.PortTaskCatalog;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.DockChecklist;
import com.harbourmaster.model.HarbourmasterSnapshot;
import com.harbourmaster.model.OfferScore;
import com.harbourmaster.model.RoutePlan;
import com.harbourmaster.optimizer.OfferRanker;
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
import com.harbourmaster.tracker.PortTracker;
import com.harbourmaster.tracker.RouteTracker;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "Harbourmaster",
        description = "Optimizes Sailing courier tasks, noticeboard choices, routes, cargo and dock actions",
        tags = {"sailing", "port", "courier", "cargo", "tasks", "route", "optimizer", "navigation"})
public class HarbourmasterPlugin extends Plugin {
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
    private final RouteOptimizer optimizer = new RouteOptimizer(new PortGraph(PortPathData.load()));
    private final RouteTracker routeTracker = new RouteTracker(optimizer);
    private final OfferRanker ranker = new OfferRanker(optimizer);
    private volatile HarbourmasterSnapshot snapshot = HarbourmasterSnapshot.empty();
    private volatile boolean running;
    private List<Object> previousInputs;

    @Provides
    HarbourmasterConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(HarbourmasterConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        running = true;
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
    protected void shutDown() throws Exception {
        running = false;
        for (Overlay overlay : overlays()) {
            overlayManager.remove(overlay);
        }
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
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            clear();
        } else if (event.getGameState() == GameState.LOGGED_IN) {
            ports.scanScene(client);
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
        routeTracker.clear();
        previousInputs = null;
        snapshot = HarbourmasterSnapshot.empty();
    }

    private void refresh() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (!catalog.isLoaded()) {
            catalog.load(client);
        }
        noticeboard.scan(client, catalog);
        ports.update(client, noticeboard.isOpen() ? noticeboard.getPort() : null);
        List<ActiveTask> held = activeTasks.read(
                client::getVarbitValue, client::getVarpValue, catalog::byId, catalog::isIgnoredTask, ports.getStart());
        Map<Integer, CargoTracker.Destination> destinations = cargo.destinations(held, ports.getDock());
        boolean depositCargo = cargo.needsDeposit(client, destinations, ports.getDock());
        int level = client.getRealSkillLevel(Skill.SAILING);
        int capacity = Math.min(5, 1 + client.getVarbitValue(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED));
        int freeSlots = Math.max(0, capacity - activeTasks.occupiedSlots(client::getVarbitValue));
        List<Object> inputs = Arrays.asList(
                held,
                noticeboard.getOffers(),
                noticeboard.getPort(),
                noticeboard.isOpen(),
                ports.getStart(),
                ports.getBoatPosition(),
                ports.getDock(),
                level,
                freeSlots,
                depositCargo,
                config.enableOptimizer());
        if (inputs.equals(previousInputs)) {
            return;
        }
        previousInputs = inputs;
        RoutePlan route;
        if (config.enableOptimizer()) {
            route = routeTracker.update(ports.getStart(), ports.getBoatPosition(), held);
        } else {
            routeTracker.clear();
            route = RoutePlan.unavailable("Route optimiser disabled");
        }
        boolean rankOffers = noticeboard.isOpen() && config.rankOffers();
        RoutePlan base =
                rankOffers && config.enableOptimizer() ? optimizer.optimize(noticeboard.getPort(), held) : route;
        List<OfferScore> offers = rankOffers
                ? ranker.rank(noticeboard.getPort(), held, noticeboard.getOffers(), level, freeSlots, base)
                : List.of();
        snapshot = new HarbourmasterSnapshot(
                true,
                route,
                offers,
                noticeboard.isOpen(),
                freeSlots,
                DockChecklist.at(ports.getDock(), held),
                destinations,
                depositCargo);
    }

    public HarbourmasterSnapshot getSnapshot() {
        return snapshot;
    }

    public NoticeboardTracker getNoticeboard() {
        return noticeboard;
    }

    public PortTracker getPorts() {
        return ports;
    }
}
