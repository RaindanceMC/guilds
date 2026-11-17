package de.raindancer.modules.guilds.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The name and tag a creator's own guild is founded under: their name, then numbered when that is taken, and a tag
 * from the letters and digits of their name. They can change both in /guild.
 */
public final class OwnGuildRule implements IGuildsRule {

    @Override
    public String describe() {
        return "a creator's own guild is named after them, and its tag is the start of their name in capitals";
    }

    /** Names to try, in order. */
    public List<String> names(String player) {
        List<String> names = new ArrayList<>();
        names.add(player + "'s Guild");
        for (int n = 2; n <= 9; n++) {
            names.add(player + "'s Guild " + n);
        }
        names.add(player);
        return names;
    }

    /** Tags to try, in order, none longer than {@code longest}. */
    public List<String> tags(String player, int longest) {
        String letters = player.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (letters.isEmpty()) {
            letters = "GUILD";
        }
        int size = Math.max(1, longest);
        Set<String> tags = new LinkedHashSet<>();
        tags.add(letters.substring(0, Math.min(size, letters.length())));
        String stem = letters.substring(0, Math.min(Math.max(1, size - 1), letters.length()));
        for (int n = 2; n <= 9; n++) {
            tags.add((stem + n).substring(0, Math.min(size, stem.length() + 1)));
        }
        return List.copyOf(tags);
    }
}
