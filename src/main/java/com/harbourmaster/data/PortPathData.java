package com.harbourmaster.data;

import com.harbourmaster.model.Port;
import com.harbourmaster.model.RouteLeg;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

public final class PortPathData {
    private PortPathData() {}

    public static List<RouteLeg> load() {
        List<RouteLeg> paths = new ArrayList<>();
        try (BufferedReader reader = resource("port-paths.tsv")) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t");
                Port from = Port.valueOf(fields[0]);
                Port to = Port.valueOf(fields[1]);
                List<WorldPoint> points = new ArrayList<>();
                double distance = 0;
                for (String point : fields[2].split(" ")) {
                    String[] coordinates = point.split(",");
                    WorldPoint next =
                            new WorldPoint(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), 0);
                    if (!points.isEmpty()) {
                        WorldPoint previous = points.get(points.size() - 1);
                        distance += Math.hypot(next.getX() - previous.getX(), next.getY() - previous.getY());
                    }
                    points.add(next);
                }
                paths.add(new RouteLeg(from, to, distance, points));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return List.copyOf(paths);
    }

    static BufferedReader resource(String name) {
        return new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(PortPathData.class.getResourceAsStream("/com/harbourmaster/" + name)),
                StandardCharsets.UTF_8));
    }
}
