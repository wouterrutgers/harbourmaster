package com.harbourmaster.data;

import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class SailingShortcut {
    // Portal centres checked against OpenRS2 cache 2720. Exit positions from
    // https://github.com/NathanVegetable/barracuda-trial (GwenithGlideRoutes).
    // These directed exits also work outside a trial. Headings use east = 0, north = 2.
    public static final List<SailingShortcut> GWENITH = List.of(
            new SailingShortcut("Black portal", new WorldPoint(2105, 3424, 0), 6, new WorldPoint(2198, 3584, 0), 4),
            new SailingShortcut(
                    "Light blue portal", new WorldPoint(2142, 3582, 0), 4, new WorldPoint(2137, 3253, 0), 2));

    public final String name;
    public final WorldPoint entrance;
    public final int entryHeading;
    public final WorldPoint exit;
    public final int exitHeading;

    public SailingShortcut(String name, WorldPoint entrance, int entryHeading, WorldPoint exit, int exitHeading) {
        this.name = name;
        this.entrance = entrance;
        this.entryHeading = entryHeading;
        this.exit = exit;
        this.exitHeading = exitHeading;
    }

    public WorldPoint approach(BoatSize boatSize) {
        return offset(entrance, entryHeading, -(boatSize.length / 2 + 3));
    }

    public WorldPoint departure(BoatSize boatSize) {
        return offset(exit, exitHeading, boatSize.length / 2 + 3);
    }

    private static WorldPoint offset(WorldPoint point, int heading, int tiles) {
        int[] horizontal = {1, 1, 0, -1, -1, -1, 0, 1};
        int[] vertical = {0, 1, 1, 1, 0, -1, -1, -1};
        return new WorldPoint(point.getX() + horizontal[heading] * tiles, point.getY() + vertical[heading] * tiles, 0);
    }
}
