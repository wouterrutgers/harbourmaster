package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.model.CourierTask;
import java.time.Instant;
import java.util.List;
import org.junit.Test;

public class OfferCycleTrackerTest {
    @Test
    public void cachedOffersExpireAfterEightCompletedTasksAndAtDailyRollover() {
        OfferCycleTracker tracker = new OfferCycleTracker();
        CourierTask firstBoardOffer = courier(1, B, D, 100);
        CourierTask nextCycleOffer = new CourierTask(2, 2, "Task 2", 1, B, 102, "Cargo 2", 3, 100, A, D);
        long beforeMidnight = OfferCycleTracker.resetDay(Instant.parse("2026-09-23T22:59:59Z"));

        tracker.observe(7, List.of(firstBoardOffer), beforeMidnight);
        assertTrue(tracker.offers().containsKey(A));

        tracker.observe(8, List.of(nextCycleOffer), beforeMidnight);
        assertFalse(tracker.offers().containsKey(A));
        assertEquals(List.of(nextCycleOffer), tracker.offers().get(B));

        tracker.observe(0, List.of(firstBoardOffer), beforeMidnight);
        assertFalse(tracker.offers().containsKey(B));
        assertEquals(List.of(firstBoardOffer), tracker.offers().get(A));

        tracker.observe(0, List.of(nextCycleOffer), OfferCycleTracker.resetDay(Instant.parse("2026-09-23T23:00:00Z")));
        assertFalse(tracker.offers().containsKey(A));
        assertEquals(List.of(nextCycleOffer), tracker.offers().get(B));
    }
}
