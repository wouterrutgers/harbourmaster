package com.harbourmaster.tracker;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.model.CourierTask;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;

public class OfferCycleTrackerTest {
    @Test
    public void dailyResetUsesTheUkCalendarDateDuringBritishSummerTime() {
        assertEquals(
                LocalDate.of(2026, 9, 24).toEpochDay(),
                OfferCycleTracker.resetDay(Instant.parse("2026-09-23T23:00:00Z")));
    }

    @Test
    public void cachedOffersExpireAfterEightCompletedTasksAndAtDailyRollover() {
        OfferCycleTracker tracker = new OfferCycleTracker();
        CourierTask firstBoardOffer = courier(1, B, D, 100);
        CourierTask nextCycleOffer = new CourierTask(2, 2, "Task 2", 1, B, 102, "Cargo 2", 3, 100, A, D);

        tracker.observe(7, List.of(firstBoardOffer), 1);
        assertTrue(tracker.offers().containsKey(A));

        tracker.observe(8, List.of(nextCycleOffer), 1);
        assertFalse(tracker.offers().containsKey(A));
        assertEquals(List.of(nextCycleOffer), tracker.offers().get(B));

        tracker.observe(0, List.of(firstBoardOffer), 2);
        assertFalse(tracker.offers().containsKey(B));
        assertEquals(List.of(firstBoardOffer), tracker.offers().get(A));

        tracker.observe(0, List.of(nextCycleOffer), 3);
        assertFalse(tracker.offers().containsKey(A));
        assertEquals(List.of(nextCycleOffer), tracker.offers().get(B));
    }
}
