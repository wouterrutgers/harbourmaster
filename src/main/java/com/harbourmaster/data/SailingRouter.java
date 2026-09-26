package com.harbourmaster.data;

import com.harbourmaster.model.RouteLeg;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

@FunctionalInterface
public interface SailingRouter {
    Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize boatSize);
}
