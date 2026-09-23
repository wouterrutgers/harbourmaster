package com.harbourmaster.tracker;

import net.runelite.api.gameval.VarbitID;

public final class TaskVarbits {
    private TaskVarbits() {}

    public static final int[] IDS = {
        VarbitID.PORT_TASK_SLOT_0_ID,
        VarbitID.PORT_TASK_SLOT_1_ID,
        VarbitID.PORT_TASK_SLOT_2_ID,
        VarbitID.PORT_TASK_SLOT_3_ID,
        VarbitID.PORT_TASK_SLOT_4_ID
    };
    public static final int[] TAKEN = {
        VarbitID.PORT_TASK_SLOT_0_CARGO_TAKEN,
        VarbitID.PORT_TASK_SLOT_1_CARGO_TAKEN,
        VarbitID.PORT_TASK_SLOT_2_CARGO_TAKEN,
        VarbitID.PORT_TASK_SLOT_3_CARGO_TAKEN,
        VarbitID.PORT_TASK_SLOT_4_CARGO_TAKEN
    };
    public static final int[] DELIVERED = {
        VarbitID.PORT_TASK_SLOT_0_CARGO_DELIVERED,
        VarbitID.PORT_TASK_SLOT_1_CARGO_DELIVERED,
        VarbitID.PORT_TASK_SLOT_2_CARGO_DELIVERED,
        VarbitID.PORT_TASK_SLOT_3_CARGO_DELIVERED,
        VarbitID.PORT_TASK_SLOT_4_CARGO_DELIVERED
    };
}
