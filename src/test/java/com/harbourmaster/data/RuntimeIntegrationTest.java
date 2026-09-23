package com.harbourmaster.data;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.ApiStub;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.optimizer.RouteOptimizer;
import com.harbourmaster.tracker.ActiveTaskTracker;
import com.harbourmaster.tracker.NoticeboardTracker;
import com.harbourmaster.tracker.TaskVarbits;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

public class RuntimeIntegrationTest {
    private final Map<Integer, Map<String, Object>> rows = new HashMap<>();
    private boolean boardHidden;
    private boolean detailsOpen;
    private Widget[] entries = new Widget[0];
    private final Widget board = ApiStub.of(Widget.class, (method, arguments) -> {
        switch (method) {
            case "isHidden":
                return boardHidden;
            case "getDynamicChildren":
                return entries;
            default:
                throw new AssertionError(method);
        }
    });
    private final Widget detailsWindow = ApiStub.of(Widget.class, (method, arguments) -> {
        if (method.equals("isHidden")) {
            return false;
        }
        throw new AssertionError(method);
    });
    private final Client client = ApiStub.of(Client.class, (method, arguments) -> {
        switch (method) {
            case "getDBTableRows":
                return List.copyOf(rows.keySet());
            case "getDBTableField":
                Object field = rows.get(arguments[0]).get(arguments[1] + ":" + arguments[2]);
                return field == null ? new Object[0] : new Object[] {field};
            case "getItemDefinition":
                return ApiStub.of(ItemComposition.class, (itemMethod, itemArguments) -> {
                    if (itemMethod.equals("getName")) {
                        return "Runtime cargo";
                    }
                    throw new AssertionError(itemMethod);
                });
            case "getWidget":
                if (arguments[0].equals(InterfaceID.PortTaskInfo.WINDOW)) {
                    return detailsOpen ? detailsWindow : null;
                }
                return arguments[0].equals(InterfaceID.PortTaskBoard.CONTAINER) ? board : null;
            default:
                throw new AssertionError(method);
        }
    });

    @Test
    public void catalogueReadsCourierCargoFromRuntime() {
        Map<String, Object> courier = row(1, 0);
        courier.put(DBTableID.PortTask.COL_CARGO + ":0", 200);
        courier.put(DBTableID.PortTask.COL_CARGO + ":1", 3);
        rows.put(8664, courier);
        PortTaskCatalog catalog = new PortTaskCatalog();
        catalog.load(client);
        CourierTask loaded = catalog.byId(1);
        assertEquals(3, loaded.quantity);
        assertEquals(200, loaded.itemId);
        assertEquals(A, loaded.pickup);
        assertEquals(D, loaded.delivery);
    }

    @Test
    public void nonCourierRowsAreIgnoredWithoutLosingTheirOccupiedSlots() {
        Map<String, Object> courier = row(1, 0);
        courier.put(DBTableID.PortTask.COL_CARGO + ":0", 200);
        courier.put(DBTableID.PortTask.COL_CARGO + ":1", 3);
        rows.put(8664, courier);
        rows.put(99998, row(2, 1));
        PortTaskCatalog catalog = new PortTaskCatalog();
        catalog.load(client);
        Map<Integer, Integer> varbits = Map.of(TaskVarbits.IDS[0], 1, TaskVarbits.IDS[1], 2);
        ActiveTaskTracker active = new ActiveTaskTracker();
        List<ActiveTask> tasks =
                active.read(id -> varbits.getOrDefault(id, 0), id -> 0, catalog::byId, catalog::isIgnoredTask, A);
        assertEquals(1, tasks.size());
        assertEquals(2, active.occupiedSlots(id -> varbits.getOrDefault(id, 0)));
        assertTrue(new RouteOptimizer(line()).optimize(A, tasks).available);
        entries = new Widget[] {entry(new Object[] {1, 2, 3, 8664}), entry(new Object[] {1, 2, 3, 99998})};
        NoticeboardTracker board = new NoticeboardTracker();
        board.scan(client, catalog);
        assertEquals(1, board.getOffers().size());
        assertEquals(1, board.getWidgets().size());
    }

    @Test
    public void boardReadsTaskEntriesAndStopsHighlightingWhenClosed() {
        Map<String, Object> courier = row(1, 0);
        courier.put(DBTableID.PortTask.COL_CARGO + ":0", 200);
        courier.put(DBTableID.PortTask.COL_CARGO + ":1", 3);
        rows.put(8664, courier);
        PortTaskCatalog catalog = new PortTaskCatalog();
        catalog.load(client);
        entries = new Widget[] {entry(new Object[] {1, 2, 3, 8664}), entry(new Object[] {1, 2, 3, 8664}), entry(null)};
        NoticeboardTracker tracker = new NoticeboardTracker();
        tracker.scan(client, catalog);
        assertTrue(tracker.isOpen());
        assertEquals(1, tracker.getOffers().size());
        assertEquals(A, tracker.getPort());
        boardHidden = true;
        tracker.scan(client, catalog);
        assertFalse(tracker.isOpen());
        assertTrue(tracker.getWidgets().isEmpty());
    }

    @Test
    public void rebuildingBoardClearsPreviousOffersAndPort() {
        Map<String, Object> courier = row(1, 0);
        courier.put(DBTableID.PortTask.COL_CARGO + ":0", 200);
        courier.put(DBTableID.PortTask.COL_CARGO + ":1", 3);
        rows.put(8664, courier);
        PortTaskCatalog catalog = new PortTaskCatalog();
        catalog.load(client);
        entries = new Widget[] {entry(new Object[] {1, 2, 3, 8664})};
        NoticeboardTracker tracker = new NoticeboardTracker();
        tracker.scan(client, catalog);
        assertEquals(A, tracker.getPort());

        entries = null;
        tracker.scan(client, catalog);

        assertTrue(tracker.isOpen());
        assertTrue(tracker.getOffers().isEmpty());
        assertTrue(tracker.getWidgets().isEmpty());
        assertNull(tracker.getPort());
    }

    @Test
    public void openingTaskDetailsPreservesBoardOffersUntilTheWindowCloses() {
        for (int databaseRow : List.of(8664, 8665)) {
            Map<String, Object> courier = row(databaseRow - 8663, 0);
            courier.put(DBTableID.PortTask.COL_CARGO + ":0", 200);
            courier.put(DBTableID.PortTask.COL_CARGO + ":1", 3);
            rows.put(databaseRow, courier);
        }
        PortTaskCatalog catalog = new PortTaskCatalog();
        catalog.load(client);
        entries = new Widget[] {entry(new Object[] {1, 2, 3, 8664}), entry(new Object[] {1, 2, 3, 8665})};
        NoticeboardTracker tracker = new NoticeboardTracker();
        tracker.scan(client, catalog);
        tracker.beginOpeningDetails(entry(null));
        tracker.beginOpeningDetails(entries[0]);

        boardHidden = true;
        tracker.scan(client, catalog);
        assertEquals(2, tracker.getOffers().size());

        detailsOpen = true;
        entries = new Widget[] {entry(new Object[] {1, 2, 3, 8665})};
        tracker.scan(client, catalog);
        assertEquals(2, tracker.getOffers().size());

        detailsOpen = false;
        boardHidden = false;
        tracker.scan(client, catalog);
        assertEquals(1, tracker.getOffers().size());
        assertEquals(8665, tracker.getOffers().get(0).databaseRow);
    }

    private static Widget entry(Object[] listener) {
        return ApiStub.of(Widget.class, (method, arguments) -> {
            if (method.equals("getOnOpListener")) {
                return listener;
            }
            throw new AssertionError(method);
        });
    }

    private static Map<String, Object> row(int id, int type) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(DBTableID.PortTask.COL_TASK_ID + ":0", id);
        fields.put(DBTableID.PortTask.COL_NAME + ":0", "Runtime task");
        fields.put(DBTableID.PortTask.COL_TASK_TYPE + ":0", type);
        fields.put(DBTableID.PortTask.COL_LEVEL_REQUIRED + ":0", 1);
        fields.put(DBTableID.PortTask.COL_STARTING_PORT + ":0", A.databaseRow);
        fields.put(DBTableID.PortTask.COL_CARGO_PORT + ":0", A.databaseRow);
        fields.put(DBTableID.PortTask.COL_ENDING_PORT + ":0", D.databaseRow);
        return fields;
    }
}
