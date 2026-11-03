package de.raindancer.modules.guilds.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A guild's honours and what it has bought with them.
 *
 * @param levelPoints points from levels ever gained — a prestige resets the level, never these
 * @param prestige    how often the guild went past the top and started again
 * @param crowns      weeks it earned the most experience of all guilds
 * @param nodes       the rank of each skill tree node it took, by node id; missing means 0
 * @param respecAt    when it last took its points back, epoch millis; 0 for never
 */
public record Standing(int levelPoints, int prestige, int crowns, Map<String, Integer> nodes, long respecAt) {

    public static final Standing NONE = new Standing(0, 0, 0, Map.of(), 0);

    public Standing {
        levelPoints = Math.max(0, levelPoints);
        prestige = Math.max(0, prestige);
        crowns = Math.max(0, crowns);
        Map<String, Integer> copy = new HashMap<>();
        if (nodes != null) {
            nodes.forEach((node, rank) -> {
                if (node != null && rank != null && rank > 0) {
                    copy.put(node, rank);
                }
            });
        }
        nodes = Map.copyOf(copy);
    }

    public int rank(String node) {
        return nodes.getOrDefault(node, 0);
    }

    /** Ranks taken, whatever they cost. */
    public int spent() {
        return nodes.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Standing withLevelPoints(int points) {
        return new Standing(points, prestige, crowns, nodes, respecAt);
    }

    public Standing withPrestige(int times) {
        return new Standing(levelPoints, times, crowns, nodes, respecAt);
    }

    public Standing withCrowns(int won) {
        return new Standing(levelPoints, prestige, won, nodes, respecAt);
    }

    public Standing withRank(String node, int rank) {
        Map<String, Integer> next = new HashMap<>(nodes);
        next.put(node, rank);
        return new Standing(levelPoints, prestige, crowns, next, respecAt);
    }

    public Standing respecced(long now) {
        return new Standing(levelPoints, prestige, crowns, Map.of(), now);
    }
}
