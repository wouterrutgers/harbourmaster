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

        tracker.observe(7, A, List.of(firstBoardOffer), beforeMidnight);
        assertTrue(tracker.offers().containsKey(A));

        tracker.observe(8, B, List.of(nextCycleOffer), beforeMidnight);
        assertFalse(tracker.offers().containsKey(A));
        assertEquals(List.of(nextCycleOffer), tracker.offers().get(B));

        tracker.observe(0, A, List.of(firstBoardOffer), beforeMidnight);
        assertFalse(tracker.offers().containsKey(B));
        assertEquals(List.of(firstBoardOffer), tracker.offers().get(A));

        tracker.observe(0, null, List.of(), OfferCycleTracker.resetDay(Instant.parse("2026-09-23T23:00:00Z")));
        assertEquals(List.of(firstBoardOffer), tracker.offers().get(A));

        tracker.observe(
                0, B, List.of(nextCycleOffer), OfferCycleTracker.resetDay(Instant.parse("2026-09-24T00:00:00Z")));
        assertFalse(tracker.offers().containsKey(A));
        assertEquals(List.of(nextCycleOffer), tracker.offers().get(B));
    }

    @Test
    public void inspectingABoardWithoutCourierOffersReplacesItsOldOffersAndMarksItRead() {
        OfferCycleTracker tracker = new OfferCycleTracker();
        tracker.observe(0, A, List.of(courier(1, B, D, 100)), 0);

        tracker.observe(0, A, List.of(), 0);
        tracker.observe(0, null, List.of(), 0);

        assertTrue(tracker.hasObservedOffers(A));
        assertEquals(List.of(), tracker.offers().get(A));
    }
}
