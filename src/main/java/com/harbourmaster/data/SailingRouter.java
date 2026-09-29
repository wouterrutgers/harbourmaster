package com.harbourmaster.data;

import com.harbourmaster.model.RouteLeg;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

@FunctionalInterface
public interface SailingRouter {
    Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize boatSize);

    default Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize boatSize, int departure, int arrival) {
        throw new UnsupportedOperationException("This router does not support portal headings");
    }
}
