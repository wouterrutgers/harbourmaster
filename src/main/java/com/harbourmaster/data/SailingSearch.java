package com.harbourmaster.data;

import com.harbourmaster.model.RouteLeg;
import java.util.Optional;

public interface SailingSearch {
    boolean advance(int maximumExpandedStates);

    Optional<RouteLeg> result();

    int expandedStates();
}
