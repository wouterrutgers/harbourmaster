package com.harbourmaster;

import com.harbourmaster.data.PortGraph;
import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.util.List;

public final class Fixtures {
    public static final Port A = Port.PORT_SARIM;
    public static final Port B = Port.MUSA_POINT;
    public static final Port C = Port.BRIMHAVEN;
    public static final Port D = Port.CATHERBY;
    public static final Port E = Port.ARDOUGNE;

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

    public static RouteLeg edge(Port from, Port to, double length) {
        return new RouteLeg(from, to, length, List.of(from.navigationLocation, to.navigationLocation));
    }

    public static PortGraph line() {
        return new PortGraph(List.of(edge(A, B, 10), edge(B, C, 10), edge(C, D, 10), edge(B, E, 5)));
    }
}
