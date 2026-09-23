package com.harbourmaster.data;

import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

@Singleton
public final class PortTaskCatalog {
    private static final Set<Integer> SPECIAL_TASK_ROWS = Set.of(
            DBTableID.PortTask.Row.PORT_TASK_SAILING_INTRO,
            DBTableID.PortTask.Row.PORT_TASK_PRYING_TIMES,
            DBTableID.PortTask.Row.PORT_TASK_BLANK,
            DBTableID.PortTask.Row.PORT_TASK_LOCKED);
    private final TaskRewards rewards = new TaskRewards();
    private Map<Integer, CourierTask> byId = Map.of();
    private Map<Integer, CourierTask> byRow = Map.of();
    private Set<Integer> ignoredTaskIds = Set.of();
    private Set<Integer> ignoredRows = Set.of();
    private boolean loaded;

    public void load(Client client) {
        Map<Integer, CourierTask> identifiers = new HashMap<>();
        Map<Integer, CourierTask> rows = new HashMap<>();
        Set<Integer> ignoredIdentifiers = new HashSet<>();
        Set<Integer> ignoredDatabaseRows = new HashSet<>(SPECIAL_TASK_ROWS);
        for (int row : client.getDBTableRows(DBTableID.PortTask.ID)) {
            if (SPECIAL_TASK_ROWS.contains(row)) {
                continue;
            }
            if (integer(client, row, DBTableID.PortTask.COL_TASK_TYPE, 0) != 0) {
                ignoredIdentifiers.add(integer(client, row, DBTableID.PortTask.COL_TASK_ID, 0));
                ignoredDatabaseRows.add(row);
                continue;
            }
            CourierTask task = readRow(client, row);
            identifiers.put(task.id, task);
            rows.put(row, task);
        }
        byId = Map.copyOf(identifiers);
        byRow = Map.copyOf(rows);
        ignoredTaskIds = Set.copyOf(ignoredIdentifiers);
        ignoredRows = Set.copyOf(ignoredDatabaseRows);
        loaded = true;
    }

    private CourierTask readRow(Client client, int row) {
        int item = integer(client, row, DBTableID.PortTask.COL_CARGO, 0);
        return new CourierTask(
                integer(client, row, DBTableID.PortTask.COL_TASK_ID, 0),
                row,
                (String) field(client, row, DBTableID.PortTask.COL_NAME, 0),
                integer(client, row, DBTableID.PortTask.COL_LEVEL_REQUIRED, 0),
                port(client, row, DBTableID.PortTask.COL_STARTING_PORT),
                item,
                client.getItemDefinition(item).getName(),
                integer(client, row, DBTableID.PortTask.COL_CARGO, 1),
                rewards.forRow(row),
                port(client, row, DBTableID.PortTask.COL_CARGO_PORT),
                port(client, row, DBTableID.PortTask.COL_ENDING_PORT));
    }

    private static Port port(Client client, int row, int column) {
        return Objects.requireNonNull(
                Port.fromDatabaseRow(integer(client, row, column, 0)), "Unknown port in task row " + row);
    }

    private static Object field(Client client, int row, int column, int tuple) {
        return client.getDBTableField(row, column, tuple)[0];
    }

    private static int integer(Client client, int row, int column, int tuple) {
        return (Integer) field(client, row, column, tuple);
    }

    public CourierTask byId(int id) {
        return byId.get(id);
    }

    public CourierTask byRow(int row) {
        return byRow.get(row);
    }

    public boolean isIgnoredTask(int id) {
        return ignoredTaskIds.contains(id);
    }

    public boolean isIgnoredRow(int row) {
        return ignoredRows.contains(row);
    }

    public boolean isLoaded() {
        return loaded;
    }
}
