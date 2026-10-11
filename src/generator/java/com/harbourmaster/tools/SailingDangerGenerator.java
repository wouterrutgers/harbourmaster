package com.harbourmaster.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SailingDangerGenerator {
    private static final List<String> CREATURES = List.of(
            "Hammerhead_shark",
            "Tiger_shark",
            "Great_white_shark",
            "Pygmy_kraken",
            "Spined_kraken",
            "Armoured_kraken",
            "Veiled_kraken",
            "Vampyre_kraken");

    private SailingDangerGenerator() {}

    public static void main(String[] arguments) throws IOException, InterruptedException {
        generate();
    }

    static void generate() throws IOException, InterruptedException {
        StringBuilder data = new StringBuilder(
                "# Known aggressive sea creature spawns from the OSRS Wiki.\n" + "# maximum_hit\tx\ty\n");
        HttpClient http = HttpClient.newHttpClient();
        for (String creature : CREATURES) {
            String source = "https://oldschool.runescape.wiki/w/" + creature;
            HttpRequest request = HttpRequest.newBuilder(URI.create(source + "?action=raw"))
                    .header("User-Agent", "Harbourmaster route data generator")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException(
                        "Wiki creature download returned HTTP " + response.statusCode() + " for " + creature);
            }
            String page = response.body();
            Matcher aggression =
                    Pattern.compile("(?im)^\\|aggressive\\s*=\\s*yes\\s*$").matcher(page);
            Matcher maximumHit =
                    Pattern.compile("(?m)^\\|max hit\\s*=\\s*(\\d+)\\s*$").matcher(page);
            Matcher locations =
                    Pattern.compile("\\{\\{LocLine\\b[\\s\\S]*?\\}\\}").matcher(page);
            if (!aggression.find() || !maximumHit.find()) {
                throw new IllegalStateException("Missing aggressive creature combat data for " + creature);
            }
            TreeSet<String> coordinates = new TreeSet<>();
            while (locations.find()) {
                Matcher coordinate = Pattern.compile("\\|(?:x:)?(\\d{3,4}),(?:y:)?(\\d{3,4})(?=[|\\s])")
                        .matcher(locations.group());
                while (coordinate.find()) {
                    coordinates.add(coordinate.group(1) + "\t" + coordinate.group(2));
                }
            }
            if (coordinates.isEmpty()) {
                throw new IllegalStateException("Missing creature spawn coordinates for " + creature);
            }
            data.append("# ").append(source).append('\n');
            for (String coordinate : coordinates) {
                data.append(maximumHit.group(1)).append('\t').append(coordinate).append('\n');
            }
            System.out.println("Generated " + coordinates.size() + " danger locations for " + creature);
        }
        Files.writeString(
                Path.of("src/main/resources/com/harbourmaster/routes/dangers.tsv"), data, StandardCharsets.UTF_8);
    }
}
