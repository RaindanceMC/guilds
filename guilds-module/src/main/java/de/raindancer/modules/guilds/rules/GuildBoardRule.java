package de.raindancer.modules.guilds.rules;

import de.raindancer.modules.guilds.model.Guild;

import java.util.Comparator;
import java.util.List;

/** Which guilds lead a board, and how a level's progress looks on a screen. */
public final class GuildBoardRule implements IGuildsRule {

    private static final int BAR = 10;

    public enum Board {
        EXPERIENCE, BANK, MEMBERS
    }

    public List<Guild> top(List<Guild> guilds, Board board, int size) {
        Comparator<Guild> by = switch (board) {
            case EXPERIENCE -> Comparator.comparingLong(Guild::xp);
            case BANK -> Comparator.comparingLong(Guild::bank);
            case MEMBERS -> Comparator.comparingInt(guild -> guild.members().size());
        };
        return guilds.stream()
                .sorted(by.reversed().thenComparing(Guild::name, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(0, size))
                .toList();
    }

    /** Ten blocks, the done part green: {@code fraction} 0..1. */
    public String bar(double fraction) {
        int done = (int) Math.round(Math.max(0, Math.min(1, fraction)) * BAR);
        return "<green>" + "▮".repeat(done) + "<dark_gray>" + "▮".repeat(BAR - done);
    }

    @Override
    public String describe() {
        return "which guilds lead a board";
    }
}
