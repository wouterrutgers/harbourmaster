package com.harbourmaster.optimizer;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.OfferBundle;
import com.harbourmaster.model.OfferScore;
import com.harbourmaster.model.Port;
import com.harbourmaster.model.RoutePlan;
import java.util.ArrayList;
import java.util.List;

public final class OfferRanker {
    private final RouteOptimizer optimizer;

    public OfferRanker(RouteOptimizer optimizer) {
        this.optimizer = optimizer;
    }

    public List<OfferScore> rank(
            Port start,
            List<ActiveTask> held,
            List<CourierTask> offers,
            int sailingLevel,
            int freeSlots,
            RoutePlan base) {
        List<OfferScore> scores = new ArrayList<>();
        for (CourierTask task : offers) {
            String reason = eligibility(task, held, sailingLevel, freeSlots);
            if (!reason.isEmpty() || !base.available) {
                scores.add(new OfferScore(task, reason, false, 0, 0, false));
                continue;
            }
            List<ActiveTask> candidate = new ArrayList<>(held);
            candidate.add(new ActiveTask(5, task.id, task, 0, 0));
            double marginal = Math.max(0, optimizer.optimize(start, candidate).distance - base.distance);
            boolean free = marginal < 0.0000001;
            scores.add(new OfferScore(
                    task,
                    reason,
                    true,
                    free ? 0 : marginal,
                    !free && task.experience >= 0 ? task.experience / marginal : 0,
                    free));
        }
        OfferBundle bundle = bestBundle(start, held, scores, freeSlots, base);
        if (bundle != null) {
            scores.replaceAll(score -> bundle.tasks.contains(score.task) ? score.withBundle(bundle) : score);
        }
        return List.copyOf(scores);
    }

    private OfferBundle bestBundle(
            Port start, List<ActiveTask> held, List<OfferScore> scores, int freeSlots, RoutePlan base) {
        if (!base.available || freeSlots == 0) {
            return null;
        }
        List<OfferScore> eligible = new ArrayList<>();
        for (OfferScore score : scores) {
            if (score.scorable()) {
                eligible.add(score);
            }
        }
        OfferBundle best = null;
        for (int mask = 1; mask < (1 << eligible.size()); mask++) {
            if (Integer.bitCount(mask) > freeSlots) {
                continue;
            }
            List<CourierTask> tasks = new ArrayList<>();
            List<ActiveTask> candidate = new ArrayList<>(held);
            double marginal = 0;
            for (int index = 0; index < eligible.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    OfferScore score = eligible.get(index);
                    tasks.add(score.task);
                    candidate.add(new ActiveTask(5 + index, score.task.id, score.task, 0, 0));
                    marginal = score.marginalDistance;
                }
            }
            if (tasks.size() > 1) {
                marginal = Math.max(0, optimizer.optimize(start, candidate).distance - base.distance);
            }
            OfferBundle bundle = new OfferBundle(tasks, marginal);
            if (best == null
                    || (bundle.freeTravel && !best.freeTravel)
                    || (bundle.freeTravel == best.freeTravel
                            && (bundle.score > best.score
                                    || (bundle.score == best.score && bundle.experience > best.experience)))) {
                best = bundle;
            }
        }
        return best;
    }

    public static String eligibility(CourierTask offer, List<ActiveTask> held, int level, int freeSlots) {
        for (ActiveTask active : held) {
            if (active.taskId == offer.id) {
                return "Already accepted";
            }
        }
        if (level < offer.level) {
            return "Requires Sailing " + offer.level;
        }
        if (freeSlots == 0) {
            return "No free task slots";
        }
        return "";
    }
}
