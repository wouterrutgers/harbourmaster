package com.harbourmaster.tracker;

import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class OfferCycleTracker {
    private static final int TASKS_PER_OFFER_CYCLE = 8;
    private final Map<Port, List<CourierTask>> offersByPort = new EnumMap<>(Port.class);
    private int previousCompletedTasks = -1;
    private long resetDay = Long.MIN_VALUE;

    public void observe(int completedTasks, Port port, List<CourierTask> offers) {
        observe(completedTasks, port, offers, resetDay(Instant.now()));
    }

    public static long resetDay(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC).toEpochDay();
    }

    public void observe(int completedTasks, Port port, List<CourierTask> offers, long currentResetDay) {
        if (completedTasks / TASKS_PER_OFFER_CYCLE != previousCompletedTasks / TASKS_PER_OFFER_CYCLE
                || completedTasks < previousCompletedTasks
                || resetDay != currentResetDay) {
            offersByPort.clear();
        }
        previousCompletedTasks = completedTasks;
        resetDay = currentResetDay;
        if (port != null) {
            offersByPort.put(port, List.copyOf(offers));
        }
    }

    public Map<Port, List<CourierTask>> offers() {
        return Map.copyOf(offersByPort);
    }

    public boolean hasObservedOffers(Port port) {
        return offersByPort.containsKey(port);
    }

    public int tasksUntilReset() {
        return TASKS_PER_OFFER_CYCLE - previousCompletedTasks % TASKS_PER_OFFER_CYCLE;
    }

    public void clear() {
        offersByPort.clear();
        previousCompletedTasks = -1;
        resetDay = Long.MIN_VALUE;
    }
}
