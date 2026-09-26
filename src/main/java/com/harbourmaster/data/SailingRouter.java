package com.harbourmaster.data;

import com.harbourmaster.model.RouteLeg;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;

@FunctionalInterface
public interface SailingRouter {
    Optional<RouteLeg> route(WorldPoint from, WorldPoint to, BoatSize boatSize);

    default SailingSearch search(WorldPoint from, WorldPoint to, BoatSize boatSize) {
        Optional<RouteLeg> route = route(from, to, boatSize);
        return new SailingSearch() {
            @Override
            public boolean advance(int maximumExpandedStates) {
                return true;
            }

            @Override
            public Optional<RouteLeg> result() {
                return route;
            }

            @Override
            public int expandedStates() {
                return 0;
            }
        };
    }
}
