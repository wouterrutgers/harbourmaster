package com.harbourmaster.tools;

import com.harbourmaster.model.Port;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;

final class CharterDataGenerator {
    private CharterDataGenerator() {}

    static void generate() throws IOException, InterruptedException {
        String routes = parse(download("Charter_ship"), download("Template:Charter_ship_fares"));
        Files.writeString(
                Path.of("src", "main", "resources", "com", "harbourmaster", "charters.tsv"),
                routes,
                StandardCharsets.UTF_8);
        System.out.println("Generated charter routes from the wiki");
    }

    private static String download(String page) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://oldschool.runescape.wiki/w/" + page + "?action=raw"))
                .header("User-Agent", "Harbourmaster route data generator")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Wiki charter download returned HTTP " + response.statusCode() + " for " + page);
        }
        return response.body();
    }

    private static String parse(String locationSource, String fareSource) {
        Map<String, Port> ports = new HashMap<>();
        for (Port port : Port.values()) {
            ports.put(port.name, port);
        }
        Map<String, WorldPoint> locations = new HashMap<>();
        Matcher location = Pattern.compile("^\\|x:(\\d+),y:(\\d+),title:\\[\\[(.+?)\\]\\]", Pattern.MULTILINE)
                .matcher(locationSource);
        while (location.find()) {
            locations.put(
                    location.group(3),
                    new WorldPoint(Integer.parseInt(location.group(1)), Integer.parseInt(location.group(2)), 0));
        }
        List<String> destinations = new ArrayList<>();
        Matcher destination =
                Pattern.compile("^!\\[\\[(.+?)\\]\\]$", Pattern.MULTILINE).matcher(fareSource);
        while (destination.find()) {
            destinations.add(destination.group(1));
        }

        StringBuilder routes =
                new StringBuilder("# Coordinates use the wiki charter map markers for walking estimates.\n"
                        + "# origin destination coins origin_x origin_y arrival_x arrival_y\n");
        Set<String> origins = new HashSet<>();
        Matcher row = Pattern.compile(
                        "^! style=\"text-align:right;\" \\|\\[\\[(.+?)\\]\\]\\R((?:\\|(?!-)[^\\r\\n]*\\R)+)",
                        Pattern.MULTILINE)
                .matcher(fareSource);
        while (row.find()) {
            origins.add(row.group(1));
            String[] prices = row.group(2).strip().split("\\R");
            if (prices.length != destinations.size()) {
                throw new IllegalStateException("Incomplete wiki charter fares for " + row.group(1));
            }
            Port from = ports.get(row.group(1));
            if (from == null) {
                continue;
            }
            WorldPoint origin = locations.get(from.name);
            for (int index = 0; index < destinations.size(); index++) {
                Port to = ports.get(destinations.get(index));
                String price = prices[index].substring(1);
                if (to == null || "{{NA}}".equals(price)) {
                    continue;
                }
                WorldPoint arrival = locations.get(to.name);
                routes.append(String.format(
                        Locale.ROOT,
                        "%s\t%s\t%d\t%d\t%d\t%d\t%d\n",
                        from.name(),
                        to.name(),
                        Integer.parseInt(price.replace(",", "")),
                        origin.getX(),
                        origin.getY(),
                        arrival.getX(),
                        arrival.getY()));
            }
        }
        if (destinations.isEmpty() || !origins.equals(new HashSet<>(destinations))) {
            throw new IllegalStateException("Incomplete wiki charter fare table");
        }
        return routes.toString();
    }
}
