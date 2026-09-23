package com.harbourmaster.tracker;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

public final class ActiveTaskTracker {
    private static final int[] CREW_CARGO_ITEMS = {
        VarPlayerID.SAILING_CREW_HELD_CARGO_0,
        VarPlayerID.SAILING_CREW_HELD_CARGO_1,
        VarPlayerID.SAILING_CREW_HELD_CARGO_2,
        VarPlayerID.SAILING_CREW_HELD_CARGO_3,
        VarPlayerID.SAILING_CREW_HELD_CARGO_4
    };
    private static final int[] CREW_CARGO_AMOUNTS = {
        VarbitID.SAILING_CREW_HELD_CARGO_0_AMOUNT,
        VarbitID.SAILING_CREW_HELD_CARGO_1_AMOUNT,
        VarbitID.SAILING_CREW_HELD_CARGO_2_AMOUNT,
        VarbitID.SAILING_CREW_HELD_CARGO_3_AMOUNT,
        VarbitID.SAILING_CREW_HELD_CARGO_4_AMOUNT
    };

    public List<ActiveTask> read(
            IntUnaryOperator varbit,
            IntUnaryOperator varplayer,
            IntFunction<CourierTask> catalog,
            IntPredicate ignoredTask,
            Port port) {
        List<ActiveTask> active = new ArrayList<>();
        for (int slot = 0; slot < TaskVarbits.IDS.length; slot++) {
            int id = varbit.applyAsInt(TaskVarbits.IDS[slot]);
            if (id == 0 || ignoredTask.test(id)) {
                continue;
            }
            active.add(new ActiveTask(
                    slot,
                    id,
                    catalog.apply(id),
                    varbit.applyAsInt(TaskVarbits.TAKEN[slot]),
                    varbit.applyAsInt(TaskVarbits.DELIVERED[slot])));
        }

        Map<Integer, Integer> crewCargo = new HashMap<>();
        for (int crew = 0; crew < CREW_CARGO_ITEMS.length; crew++) {
            int amount = varbit.applyAsInt(CREW_CARGO_AMOUNTS[crew]);
            if (amount > 0) {
                crewCargo.merge(varplayer.applyAsInt(CREW_CARGO_ITEMS[crew]), amount, Integer::sum);
            }
        }
        for (int index = 0; index < active.size(); index++) {
            ActiveTask task = active.get(index);
            if (task.definition == null || task.isFinished() || task.definition.pickup != port) {
                continue;
            }
            int carriedByCrew = crewCargo.getOrDefault(task.definition.itemId, 0);
            if (carriedByCrew > 0) {
                active.set(
                        index,
                        new ActiveTask(
                                task.slot,
                                task.taskId,
                                task.definition,
                                Math.min(task.definition.quantity, task.cargoTaken + carriedByCrew),
                                task.cargoDelivered));
            }
        }
        return List.copyOf(active);
    }

    public int occupiedSlots(IntUnaryOperator varbit) {
        int occupied = 0;
        for (int identifier : TaskVarbits.IDS) {
            if (varbit.applyAsInt(identifier) != 0) {
                occupied++;
            }
        }
        return occupied;
    }
}
