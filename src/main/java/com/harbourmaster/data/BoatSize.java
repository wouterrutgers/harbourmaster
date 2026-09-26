package com.harbourmaster.data;

public enum BoatSize {
    RAFT(1, 3),
    SKIFF(2, 5),
    SLOOP(3, 10);

    public final int width;
    public final int length;

    BoatSize(int width, int length) {
        this.width = width;
        this.length = length;
    }
}
