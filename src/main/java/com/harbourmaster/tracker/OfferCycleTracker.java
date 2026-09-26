package com.harbourmaster.tracker;

import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class OfferCycleTracker {
    private static final int TASKS_PER_OFFER_CYCLE = 8;
    private static final ZoneId RESET_ZONE = ZoneId.of("Europe/London");
    private final Map<Port, List<CourierTask>> offersByPort = new EnumMap<>(Port.class);
    private int previousCompletedTasks = -1;
    private long resetDay = Long.MIN_VALUE;

    public void observe(int completedTasks, List<CourierTask> offers) {
        observe(completedTasks, offers, resetDay(Instant.now()));
    }

    public static long resetDay(Instant instant) {
        return LocalDate.ofInstant(instant, RESET_ZONE).toEpochDay();
    }

    public void observe(int completedTasks, List<CourierTask> offers, long currentResetDay) {
        if (completedTasks / TASKS_PER_OFFER_CYCLE != previousCompletedTasks / TASKS_PER_OFFER_CYCLE
                || completedTasks < previousCompletedTasks
                || resetDay != currentResetDay) {
            offersByPort.clear();
        }
        previousCompletedTasks = completedTasks;
        resetDay = currentResetDay;
        if (!offers.isEmpty()) {
            offersByPort.put(offers.get(0).board, List.copyOf(offers));
        }
    }

    public Map<Port, List<CourierTask>> offers() {
        return Map.copyOf(offersByPort);
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
