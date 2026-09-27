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
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

public final class CharterRoutes {
    private static final List<String[]> CONNECTIONS = load();

    private CharterRoutes() {}

    public static void add(Client client, List<TravelMethod> methods, int coins, boolean charos) {
        int discount = (charos ? 2 : 1) * (client.getVarpValue(VarPlayerID.FEVER_QUEST) >= 140 ? 2 : 1);
        for (String[] connection : CONNECTIONS) {
            Port from = Port.valueOf(connection[0]);
            Port to = Port.valueOf(connection[1]);
            int cost = Integer.parseInt(connection[2]) / discount;
            if (coins < cost || !unlocked(client, from) || !unlocked(client, to)) {
                continue;
            }
            WorldPoint crew = new WorldPoint(Integer.parseInt(connection[3]), Integer.parseInt(connection[4]), 0);
            WorldPoint arrival = new WorldPoint(Integer.parseInt(connection[5]), Integer.parseInt(connection[6]), 0);
            methods.add(new TravelMethod(
                    from,
                    to,
                    0,
                    TravelStep.Kind.CHARTER,
                    "Charter to " + to.name,
                    8 + crew.distanceTo2D(from.navigationLocation) / 2.0,
                    arrival,
                    java.util.Map.of(ItemID.COINS, cost)));
        }
    }

    private static boolean unlocked(Client client, Port port) {
        switch (port) {
            case PORT_TYRAS:
                return client.getVarpValue(VarPlayerID.REGICIDE_QUEST) >= 15;
            case PRIFDDINAS:
                return client.getVarbitValue(VarbitID.SOTE) >= 200;
            case DEEPFIN_POINT:
                return client.getVarbitValue(VarbitID.DEEPFIN_POINT_VISITED) != 0;
            case PORT_ROBERTS:
                return client.getVarbitValue(VarbitID.PORT_ROBERTS_VISITED) != 0;
            case CIVITAS_ILLA_FORTIS:
            case ALDARIN:
            case SUNSET_COAST:
                return client.getVarbitValue(VarbitID.VARLAMORE_VISITED) != 0;
            case LANDS_END:
            case PORT_PISCARILIUS:
                return client.getVarbitValue(VarbitID.ZEAH_PLAYERHASVISITED) != 0;
            case PANDEMONIUM:
                return client.getVarbitValue(VarbitID.SAILING_INTRO) >= 16;
            case SUMMER_SHORE:
                return client.getVarbitValue(VarbitID.TT) >= 12;
            case RED_ROCK:
                return client.getVarbitValue(VarbitID.TRR) >= 12;
            default:
                return true;
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
