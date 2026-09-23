package com.harbourmaster.optimizer;

import static com.harbourmaster.Fixtures.*;
import static org.junit.Assert.*;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.OfferBundle;
import com.harbourmaster.model.OfferScore;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class OfferRankerTest {
    private final RouteOptimizer optimizer = new RouteOptimizer(line());
    private final OfferRanker ranker = new OfferRanker(optimizer);

    @Test
    public void marginalValueBeatsBetterStandaloneExperiencePerTile() {
        CourierTask standaloneWinner = courier(2, B, E, 1000);
        CourierTask marginalWinner = courier(3, B, C, 100);
        List<ActiveTask> active = List.of(accepted(courier(1, A, D, 100)));
        List<OfferScore> scores =
                ranker.rank(A, active, List.of(standaloneWinner, marginalWinner), 99, 4, optimizer.optimize(A, active));
        assertEquals(List.of(marginalWinner), scores.get(1).bundle.tasks);
        assertTrue(scores.get(1).freeTravel);
        assertEquals(0, scores.get(1).marginalDistance, 0);
        assertNull(scores.get(0).bundle);
        assertEquals(10, scores.get(0).marginalDistance, 0.00001);
        assertEquals(100, scores.get(0).score, 0.00001);
    }

    @Test
    public void equalMarginalEfficiencyUsesExperienceAsTieBreaker() {
        List<OfferScore> scores = ranker.rank(
                A,
                List.of(),
                List.of(courier(1, A, B, 100), courier(2, A, C, 200)),
                99,
                1,
                optimizer.optimize(A, List.of()));
        assertNull(scores.get(0).bundle);
        assertEquals(2, scores.get(1).bundle.tasks.get(0).id);
    }

    @Test
    public void heldAndLevelGatedJobsAreNotRecommended() {
        CourierTask held = courier(1, A, D, 100);
        CourierTask gated = new CourierTask(3, 3, "High level", 99, A, 103, "Cargo", 3, 10000, A, B);
        List<ActiveTask> active = List.of(accepted(held));
        List<OfferScore> scores = ranker.rank(
                A, active, List.of(held, gated, courier(5, B, C, 100)), 50, 4, optimizer.optimize(A, active));
        assertNull(scores.get(0).bundle);
        assertNull(scores.get(1).bundle);
        assertEquals(5, scores.get(2).bundle.tasks.get(0).id);
    }

    @Test
    public void unknownExperienceIsNotRecommended() {
        List<CourierTask> offers = List.of(courier(1, A, B, -1));
        List<OfferScore> scores = ranker.rank(A, List.of(), offers, 99, 5, optimizer.optimize(A, List.of()));
        assertNull(scores.get(0).bundle);
    }

    @Test
    public void sharedCrossingBundleBeatsTheBestIndividualOfferAndRespectsCapacity() {
        CourierTask individual = courier(1, A, B, 150);
        CourierTask outbound = courier(2, A, D, 300);
        CourierTask companion = courier(3, A, D, 300);
        List<CourierTask> offers = List.of(individual, outbound, companion);
        List<OfferScore> scores = ranker.rank(A, List.of(), offers, 99, 2, optimizer.optimize(A, List.of()));
        assertNull(scores.get(0).bundle);
        OfferBundle bundle = scores.get(1).bundle;
        assertNotNull(scores.get(2).bundle);
        assertEquals(Set.of(outbound, companion), Set.copyOf(bundle.tasks));
        assertEquals(600, bundle.experience);
        assertEquals(30, bundle.marginalDistance, 0.00001);
        assertEquals(20, bundle.score, 0.00001);
        List<OfferScore> single = ranker.rank(A, List.of(), offers, 99, 1, optimizer.optimize(A, List.of()));
        assertEquals(List.of(individual), single.get(0).bundle.tasks);
        assertNull(single.get(1).bundle);
        assertNull(single.get(2).bundle);
        List<ActiveTask> held = List.of(accepted(outbound));
        scores = ranker.rank(A, held, offers, 99, 2, optimizer.optimize(A, held));
        OfferBundle remaining = scores.stream()
                .filter(score -> score.bundle != null)
                .findFirst()
                .orElseThrow()
                .bundle;
        assertEquals(Set.of(individual, companion), Set.copyOf(remaining.tasks));
        assertTrue(remaining.freeTravel);
        assertEquals(450, remaining.experience);
    }

    @Test
    public void fullSlotsDoNotRecommendAcceptingAnotherTask() {
        List<OfferScore> scores =
                ranker.rank(A, List.of(), List.of(courier(1, A, B, 100)), 99, 0, optimizer.optimize(A, List.of()));
        assertFalse(scores.get(0).eligible());
        assertNull(scores.get(0).bundle);
    }
}
