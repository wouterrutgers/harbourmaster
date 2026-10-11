package com.harbourmaster.data;

import com.harbourmaster.model.RouteLeg;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

public final class SailingDangers {
    // Allow room for creatures to wander and notice a passing boat.
    private static final int CLEARANCE = 16;
    private final List<Danger> dangers;
    private final Map<Integer, byte[]> maximumHits = new HashMap<>();

    SailingDangers(List<Danger> dangers) {
        this.dangers = List.copyOf(dangers);
        for (Danger danger : dangers) {
            for (int x = danger.x - CLEARANCE; x <= danger.x + CLEARANCE; x++) {
                for (int y = danger.y - CLEARANCE; y <= danger.y + CLEARANCE; y++) {
                    byte[] region = maximumHits.computeIfAbsent((x >> 6) * 256 + (y >> 6), ignored -> new byte[4096]);
                    int tile = (x & 63) * 64 + (y & 63);
                    region[tile] = (byte) Math.max(region[tile], danger.maximumHit);
                }
            }
        }
    }

    public static SailingDangers load() {
        return Bundled.INSTANCE;
    }

    public boolean blocks(int x, int y, int damageReduction) {
        if (damageReduction < 0) {
            return false;
        }
        byte[] region = maximumHits.get((x >> 6) * 256 + (y >> 6));
        return region != null && region[(x & 63) * 64 + (y & 63)] > damageReduction;
    }

    public boolean allows(RouteLeg route, BoatSize boatSize, int damageReduction) {
        if (damageReduction < 0) {
            return true;
        }
        // Cached routes need clearance for every heading and turn, including the bow and stern.
        double radius = CLEARANCE + Math.hypot(boatSize.length / 2.0, boatSize.width / 2.0) + 0.5;
        for (Danger danger : dangers) {
            if (danger.maximumHit <= damageReduction) {
                continue;
            }
            Rectangle2D area = new Rectangle2D.Double(danger.x - radius, danger.y - radius, radius * 2, radius * 2);
            for (int index = 0; index < route.points.size(); index++) {
                WorldPoint point = route.points.get(index);
                if (area.contains(point.getX(), point.getY())) {
                    return false;
                }
                if (index > 0) {
                    WorldPoint previous = route.points.get(index - 1);
                    if (area.intersectsLine(previous.getX(), previous.getY(), point.getX(), point.getY())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static final class Danger {
        final int maximumHit;
        final int x;
        final int y;

        Danger(int maximumHit, int x, int y) {
            this.maximumHit = maximumHit;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Bundled {
        private static final SailingDangers INSTANCE = read();

        private static SailingDangers read() {
            InputStream resource = SailingDangers.class.getResourceAsStream("/com/harbourmaster/routes/dangers.tsv");
            if (resource == null) {
                throw new IllegalStateException("Missing generated sailing dangers");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                List<Danger> dangers = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split("\t");
                    dangers.add(new Danger(
                            Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2])));
                }
                return new SailingDangers(dangers);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read generated sailing dangers", exception);
            }
        }
    }
}
