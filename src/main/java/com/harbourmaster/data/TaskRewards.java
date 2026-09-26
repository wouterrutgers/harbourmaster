package com.harbourmaster.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TaskRewards {
    private final Map<Integer, Integer> experience = new HashMap<>();

    public TaskRewards() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(TaskRewards.class.getResourceAsStream("/com/harbourmaster/task-rewards.tsv")),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t");
                experience.put(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public int forRow(int databaseRow) {
        return experience.getOrDefault(databaseRow, -1);
    }
}
