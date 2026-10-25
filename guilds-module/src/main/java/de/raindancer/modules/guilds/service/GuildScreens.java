package de.raindancer.modules.guilds.service;

import de.raindancer.core.RainsCore;
import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.rules.GuildBoardRule;
import de.raindancer.modules.guilds.rules.LevelRule;
import de.raindancer.modules.guilds.store.GuildBook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;

/** The guilds' screens for Core's {@code /screen}: everybody's own guild and its members, and three guild boards. */
public final class GuildScreens {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int BOARD_SIZE = 10;
    private static final int MOST_NAMES = 16;

    private final GuildBook book;
    private final GuildService guilds;
    private final LongFunction<String> money;
    private final Server server;
    private final GuildBoardRule rule = new GuildBoardRule();

    public GuildScreens(GuildBook book, GuildService guilds, LongFunction<String> money, Server server) {
        this.book = book;
        this.guilds = guilds;
        this.money = money;
        this.server = server;
    }

    public static final List<String> KINDS = List.of("guild-you", "guild-members", "guild-top", "guild-bank",
            "guild-biggest");

    public void register(Screens screens, Plugin owner) {
        screens.register(owner, ScreenKind.personal("guild-you", "Your guild",
                "Everybody's own guild: level and progress, bank, who of it is on, its message.",
                Material.WHITE_BANNER, viewer -> yours(viewer)));
        screens.register(owner, ScreenKind.personal("guild-members", "Your guild's members online",
                "Everybody sees who of their own guild is on right now.", Material.PLAYER_HEAD, viewer -> members(viewer)));
        screens.register(owner, ScreenKind.everyone("guild-top", "Top guilds",
                "The guilds with the most experience, and their levels.", Material.GOLDEN_HELMET,
                () -> board(GuildBoardRule.Board.EXPERIENCE)));
        screens.register(owner, ScreenKind.everyone("guild-bank", "Richest guilds", "The guilds with the most in their bank.",
                Material.GOLD_BLOCK, () -> board(GuildBoardRule.Board.BANK)));
        screens.register(owner, ScreenKind.everyone("guild-biggest", "Biggest guilds", "The guilds with the most members.",
                Material.IRON_CHESTPLATE, () -> board(GuildBoardRule.Board.MEMBERS)));
    }

    private Component yours(ScreenKind.Viewer viewer) {
        var found = book.guildOf(viewer.id());
        if (found.isEmpty()) {
            return MINI.deserialize("<gold><bold>No guild yet</bold></gold>\n<gray>Found or join one with <white>/guild");
        }
        Guild guild = found.get();
        LevelRule levels = guilds.levels();
        int level = guilds.level(guild);
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.ENGLISH);
        String progress = levels.isTop(level) ? "<gold>top level"
                : rule.bar(levels.progress(guild.xp())) + " <gray>" + numbers.format(guild.xp()) + "/"
                + numbers.format(levels.toReach(level + 1)) + " XP";
        var text = Component.text()
                .append(GuildNotifier.tag(guild)).append(Component.space()).append(GuildNotifier.title(guild))
                .append(Component.newline())
                .append(MINI.deserialize("<gray>Level <white>" + level + "</white> " + progress))
                .append(Component.newline())
                .append(MINI.deserialize("<gray>Bank <white>" + MINI.escapeTags(money.apply(guild.bank()))))
                .append(Component.newline())
                .append(MINI.deserialize("<gray>On now <white>" + online(guild).size() + "</white>/" + guild.members().size()));
        if (!guild.motd().isBlank()) {
            text.append(Component.newline()).append(MINI.deserialize("<italic><gray>" + MINI.escapeTags(guild.motd())));
        }
        return text.build();
    }

    private Component members(ScreenKind.Viewer viewer) {
        var found = book.guildOf(viewer.id());
        if (found.isEmpty()) {
            return MINI.deserialize("<gray>You are in no guild.");
        }
        Guild guild = found.get();
        List<String> on = online(guild).stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        var text = Component.text().append(GuildNotifier.tag(guild))
                .append(MINI.deserialize(" <white>on now: " + on.size()));
        for (int i = 0; i < Math.min(MOST_NAMES, on.size()); i++) {
            text.append(MINI.deserialize((i % 2 == 0 ? "\n<white>" : " <dark_gray>·</dark_gray> <white>")
                    + MINI.escapeTags(on.get(i))));
        }
        if (on.size() > MOST_NAMES) {
            text.append(MINI.deserialize("\n<gray>and " + (on.size() - MOST_NAMES) + " more"));
        }
        return text.build();
    }

    private List<Player> online(Guild guild) {
        var vanish = RainsCore.get().vanish();
        return server.getOnlinePlayers().stream()
                .filter(player -> guild.members().containsKey(player.getUniqueId()))
                .filter(player -> vanish == null || !vanish.isVanished(player.getUniqueId()))
                .map(Player.class::cast).toList();
    }

    private Component board(GuildBoardRule.Board board) {
        String heading = switch (board) {
            case EXPERIENCE -> "Top guilds";
            case BANK -> "Richest guilds";
            case MEMBERS -> "Biggest guilds";
        };
        var text = Component.text().append(MINI.deserialize("<gold><bold>" + heading + "</bold></gold>"));
        List<Guild> top = rule.top(book.all(), board, BOARD_SIZE);
        if (top.isEmpty()) {
            text.append(MINI.deserialize("\n<gray>No guilds yet"));
        }
        for (int i = 0; i < top.size(); i++) {
            Guild guild = top.get(i);
            String worth = switch (board) {
                case EXPERIENCE -> "Lv " + guilds.level(guild);
                case BANK -> MINI.escapeTags(money.apply(guild.bank()));
                case MEMBERS -> guild.members().size() + " members";
            };
            text.append(Component.newline())
                    .append(MINI.deserialize(colour(i + 1) + (i + 1) + ". "))
                    .append(GuildNotifier.tag(guild)).append(Component.space()).append(GuildNotifier.title(guild))
                    .append(MINI.deserialize("<gray> — <white>" + worth));
        }
        return text.build();
    }

    private static String colour(int place) {
        return switch (place) {
            case 1 -> "<gold>";
            case 2 -> "<gray>";
            case 3 -> "<#cd7f32>";
            default -> "<white>";
        };
    }
}
