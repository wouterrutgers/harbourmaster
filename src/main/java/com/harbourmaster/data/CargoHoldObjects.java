package com.harbourmaster.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ObjectID;

public final class CargoHoldObjects {
    private static final Set<Integer> RAFT_IDS = Set.of(
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT,
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_CARGO);
    private static final Set<Integer> SKIFF_IDS = Set.of(
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5,
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_CARGO);
    private static final Set<Integer> SLOOP_IDS = Set.of(
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_NO_CARGO,
            ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_CARGO);

    public static final Set<Integer> IDS;

    static {
        Set<Integer> ids = new HashSet<>(RAFT_IDS);
        ids.addAll(SKIFF_IDS);
        ids.addAll(SLOOP_IDS);
        IDS = Collections.unmodifiableSet(ids);
    }

    private CargoHoldObjects() {}

    public static BoatSize boatSizeForObject(int objectId) {
        if (RAFT_IDS.contains(objectId)) {
            return BoatSize.RAFT;
        }
        if (SKIFF_IDS.contains(objectId)) {
            return BoatSize.SKIFF;
        }
        if (SLOOP_IDS.contains(objectId)) {
            return BoatSize.SLOOP;
        }
        throw new IllegalArgumentException("Unknown cargo hold object: " + objectId);
    }
}
