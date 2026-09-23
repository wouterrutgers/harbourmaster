package com.harbourmaster.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

public final class TaskRewards {
    private final Map<Integer, Integer> experience = new HashMap<>();

    public TaskRewards() {
        try (BufferedReader reader = PortPathData.resource("task-rewards.tsv")) {
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
