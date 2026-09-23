package com.harbourmaster.model;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class NavigationPath {
    public final RouteLeg leg;
    public final List<Sample> points;

    public NavigationPath(RouteLeg leg) {
        this.leg = leg;
        List<Sample> sampled = new ArrayList<>();
        for (int index = 1; index < leg.points.size(); index++) {
            WorldPoint from = leg.points.get(index - 1);
            WorldPoint to = leg.points.get(index);
            int count = Math.max(1, (int) Math.ceil(Math.hypot(to.getX() - from.getX(), to.getY() - from.getY()) / 4));
            for (int step = 0; step < count; step++) {
                double fraction = (double) step / count;
                sampled.add(new Sample(
                        from.getX() + (to.getX() - from.getX()) * fraction,
                        from.getY() + (to.getY() - from.getY()) * fraction,
                        from.getPlane()));
            }
        }
        if (!leg.points.isEmpty()) {
            WorldPoint last = leg.points.get(leg.points.size() - 1);
            sampled.add(new Sample(last.getX(), last.getY(), last.getPlane()));
        }
        points = List.copyOf(sampled);
    }

    public static final class Sample {
        public final WorldPoint tile;
        public final double offsetX;
        public final double offsetY;

        public Sample(double x, double y, int plane) {
            tile = new WorldPoint((int) Math.floor(x), (int) Math.floor(y), plane);
            offsetX = x - tile.getX();
            offsetY = y - tile.getY();
        }
    }
}
