package com.harbourmaster;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.List;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

public final class Fixtures {
    public static final Port A = Port.PORT_SARIM;
    public static final Port B = Port.MUSA_POINT;
    public static final Port C = Port.BRIMHAVEN;
    public static final Port D = Port.CATHERBY;
    public static final Port E = Port.ARDOUGNE;
    private static final List<Port> LINE_PORTS = List.of(A, B, C, D, E);
    private static final double[][] LINE_DISTANCES = {
        {0, 10, 20, 30, 15},
        {10, 0, 10, 20, 5},
        {20, 10, 0, 10, 15},
        {30, 20, 10, 0, 25},
        {15, 5, 15, 25, 0}
    };

    private Fixtures() {}

    public static CourierTask courier(int id, Port pickup, Port delivery, int experience) {
        return new CourierTask(id, id, "Task " + id, 1, A, 100 + id, "Cargo " + id, 3, experience, pickup, delivery);
    }

    public static ActiveTask accepted(CourierTask task) {
        return new ActiveTask(task.id, task.id, task, 0, 0);
    }

    public static ActiveTask loaded(CourierTask task) {
        return new ActiveTask(task.id, task.id, task, task.quantity, 0);
    }

    public static PortGraph line() {
        return new PortGraph((from, to, boatSize) ->
                        Optional.of(new RouteLeg(null, null, distance(from, to), List.of(from, to))))
                .detachedSnapshot(null);
    }

    private static double distance(WorldPoint from, WorldPoint to) {
        Port start = port(from);
        Port destination = port(to);
        if (start != null && destination != null) {
            return LINE_DISTANCES[LINE_PORTS.indexOf(start)][LINE_PORTS.indexOf(destination)];
        }
        return Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
    }

    private static Port port(WorldPoint point) {
        for (Port port : LINE_PORTS) {
            if (port.navigationLocation.equals(point)) {
                return port;
            }
        }
        return null;
    }
}
