package com.harbourmaster.tracker;

import java.time.Instant;

public final class GuidanceActivityTracker {
    private Instant activeUntil = Instant.MIN;
    private boolean wasActive;

    public boolean update(boolean hasTasks, boolean browsingNoticeboard, Instant now) {
        boolean active = hasTasks || browsingNoticeboard;
        if (active || wasActive) {
            activeUntil = now.plusSeconds(60);
        }
        wasActive = active;
        return now.isBefore(activeUntil);
    }

    public void clear() {
        activeUntil = Instant.MIN;
        wasActive = false;
    }
}
