package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.TravelMethod;
import com.harbourmaster.model.TravelStep;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

public final class CharterRoutes {
    private static final List<String[]> CONNECTIONS = load();

    private CharterRoutes() {}

    public static void add(List<TravelMethod> methods) {
        for (String[] connection : CONNECTIONS) {
            Port from = Port.valueOf(connection[0]);
            Port to = Port.valueOf(connection[1]);
            WorldPoint crew = new WorldPoint(Integer.parseInt(connection[3]), Integer.parseInt(connection[4]), 0);
            WorldPoint arrival = new WorldPoint(Integer.parseInt(connection[5]), Integer.parseInt(connection[6]), 0);
            methods.add(new TravelMethod(
                    from,
                    to,
                    0,
                    TravelStep.Kind.CHARTER,
                    "Charter to " + to.name,
                    8 + crew.distanceTo2D(from.navigationLocation) / 2.0,
                    arrival));
        }
    }

    private static List<String[]> load() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                CharterRoutes.class.getResourceAsStream("/com/harbourmaster/charters.tsv"), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.startsWith("#"))
                    .map(line -> line.split("\t"))
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read charter routes", exception);
        }
    }
}
