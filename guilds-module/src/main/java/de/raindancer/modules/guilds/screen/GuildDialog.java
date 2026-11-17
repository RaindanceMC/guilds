package de.raindancer.modules.guilds.screen;

import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.dialog.DialogReturn;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.modules.guilds.GuildsServices;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildPermission;
import de.raindancer.modules.guilds.model.Rank;
import de.raindancer.modules.guilds.rules.LevelRule;
import de.raindancer.modules.guilds.util.PermissionNodes;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /guild} as the game's own window, like /hub and /cosmetics: the guild's banner and what it is on top, then a
 * button for each of its pages, greyed with the reason where the viewer's rank may not. The pages themselves stay
 * chest pages; their Back brings this window back, built fresh. Somebody in no guild gets founding, every guild and
 * their invitations instead.
 */
public final class GuildDialog implements IGuildsScreen {

    static final String HEADING = "<gradient:#FDE68A:#FDBA74>";
    private static final String HOW = "<gray>How this works";

    /** One button: what it says, what a hover tells, whether the viewer may, and why not. */
    record Door(String label, String tooltip, boolean allowed, String refusal, Runnable press) {

        static Door open(String label, String tooltip, Runnable press) {
            return new Door(label, tooltip, true, null, press);
        }

        static Door locked(String label, String tooltip, String refusal, Runnable press) {
            return new Door(label, tooltip, false, refusal, press);
        }

        static Door when(boolean allowed, String label, String tooltip, String refusal, Runnable press) {
            return new Door(label, tooltip, allowed, allowed ? null : refusal, press);
        }
    }

    private final GuildsServices services;
    private final Player viewer;

    public GuildDialog(GuildsServices services, Player viewer) {
        this.services = services;
        this.viewer = viewer;
    }

    /** The right first window for this player: their guild's, or the one for somebody in none. */
    public static IGuildsScreen forViewer(GuildsServices services, Player viewer) {
        return new GuildDialog(services, viewer);
    }

    /** The parent for a chest page opened from here: Back shows this window again. */
    static Menu returning(GuildsServices services, Player viewer) {
        return new DialogReturn(viewer, services.brand(), "Guild", () -> new GuildDialog(services, viewer).open());
    }

    @Override
    public void open() {
        Optional<Guild> guild = services.guilds().guildOf(viewer.getUniqueId());
        (guild.isPresent() ? guildPage(guild.get()) : noGuildPage()).show(viewer);
    }

    // ------------------------------------------------------------------ the windows, from plain parts

    static DialogPage page(Player viewer, String name, Supplier<ItemStack> banner, String card, List<Door> doors,
                           Runnable leave) {
        return page(viewer, name, banner, card, doors,
                List.of("<gray>Your rank and what you put in the bank stay with the guild."), leave, null);
    }

    static DialogPage page(Player viewer, String name, Supplier<ItemStack> banner, String card, List<Door> doors,
                           List<String> leaving, Runnable leave, Runnable again) {
        DialogPage page = DialogPage.titled("<bold>" + HEADING + escaped(name) + "</gradient></bold>").columns(2);
        if (banner != null) {
            page.item(banner, card);
        } else {
            page.text(card);
        }
        doors.forEach(door -> page.button(door.allowed(), door.label(), door.tooltip(), door.refusal(),
                answers -> door.press().run()));
        Runnable back = again != null ? again : () -> page.show(viewer);
        page.button(HOW, "<gray>What each of these is, and the commands.\n\n<dark_gray>» <white>Click to read it",
                answers -> help(viewer, GUILD_HELP, back));
        page.button("<red>Leave the guild", "<gray>You can be invited back.\n<dark_gray>You will be asked first."
                        + "\n\n<dark_gray>» <white>Click to leave",
                answers -> DialogPage.titled(HEADING + "Guild</gradient>", "Leave")
                        .text("<white>Leave " + escaped(name) + "?")
                        .text(String.join("\n", leaving))
                        .confirm("Yes, leave", leave, "No, stay", back, () -> { })
                        .show(viewer));
        return page;
    }

    static DialogPage noGuild(Player viewer, List<Door> doors) {
        DialogPage page = DialogPage.titled("<bold>" + HEADING + "Guilds</gradient></bold>").columns(2)
                .text("<gray>You are in no guild yet. Found your own, look at those there are, or answer an invitation.");
        doors.forEach(door -> page.button(door.allowed(), door.label(), door.tooltip(), door.refusal(),
                answers -> door.press().run()));
        page.button(HOW, "<gray>What guilds are, and the commands.\n\n<dark_gray>» <white>Click to read it",
                answers -> help(viewer, NO_GUILD_HELP, () -> page.show(viewer)));
        return page;
    }

    private static final List<String> GUILD_HELP = List.of(
            "<white>Your guild, in three parts:",
            "<gray>the people: members, invitations, ranks and allies;",
            "<gray>what it holds: the bank, the home and the log;",
            "<gray>and the guild itself: its choices, level, perks and enchantments.",
            "",
            "<gray>A greyed button says why your rank cannot use it.",
            "<gray>Leaving asks first. <white>/guild help</white> lists every command.");

    private static final List<String> NO_GUILD_HELP = List.of(
            "<white>A guild is a group with a shared bank, a home, ranks and perks.",
            "<gray>Found your own, ask to join one from the list, or accept an invitation.",
            "<gray><white>/guild help</white> lists every command.");

    private static void help(Player viewer, List<String> lines, Runnable back) {
        DialogPage.titled(HEADING + "Guild</gradient>", "How this works").text(String.join("\n", lines))
                .back(back).show(viewer);
    }

    // ------------------------------------------------------------------ the live windows

    private DialogPage guildPage(Guild guild) {
        UUID me = viewer.getUniqueId();
        LevelRule levels = services.guilds().levels();
        int level = levels.levelOf(guild.xp());
        Rank rank = guild.rankOf(me).orElse(guild.lowestRank());
        Menu here = returning(services, viewer);
        List<Door> doors = new ArrayList<>();

        doors.add(Door.open("<white>Members", "<gray>" + guild.size() + " of " + services.guilds().memberCap(guild)
                        + ".\n<dark_gray>Promote, demote or remove those below you."
                        + "\n\n<dark_gray>» <white>Click to see everybody",
                () -> new MembersMenu(services, viewer, here).open()));
        int applications = services.guilds().applicationsTo(guild).size();
        doors.add(Door.when(guild.may(me, GuildPermission.INVITE), "<white>Invite and applications",
                "<gray>" + applications + (applications == 1 ? " application" : " applications") + " waiting."
                        + "\n\n<dark_gray>» <white>Click to invite somebody or answer them",
                "Your rank cannot invite.", () -> new RecruitMenu(services, viewer, here).open()));
        doors.add(Door.open("<white>Ranks", "<gray>" + guild.ranks().size() + " ranks. You are <white>"
                        + escaped(rank.name()) + "<gray>.\n\n<dark_gray>» <white>Click to see what each may do",
                () -> new RanksMenu(services, viewer, here).open()));
        doors.add(Door.open("<white>Allies and rivals", "<gray>" + guild.allies() + " of "
                        + services.relations().allySlots(guild) + " ally slots used."
                        + "\n\n<dark_gray>» <white>Click to see every guild and how you stand",
                () -> new RelationsMenu(services, viewer, here).open()));

        doors.add(Door.when(services.bank().isOpen(), "<white>Bank", "<gray>Holds <white>"
                        + services.bank().format(guild.bank()) + "<gray>.\n\n<dark_gray>» <white>Click to pay in or take out",
                "There is no bank on this server right now.", () -> new BankMenu(services, viewer, here).open()));
        boolean unlocked = services.homes().unlocked(guild);
        boolean mayUseHome = unlocked && guild.home().isPresent() && guild.may(me, GuildPermission.USE_HOME);
        doors.add(Door.when(mayUseHome, "<white>Go to the guild home",
                guild.home().map(home -> "<gray>" + escaped(home.describe())).orElse("<gray>Not set yet.")
                        + "\n\n<dark_gray>» <white>Click to go there",
                !unlocked ? "Unlocked at level " + services.homes().unlockedAt() + "."
                        : guild.home().isEmpty() ? "Nobody has set one yet." : "Your rank cannot use it.",
                () -> {
                    viewer.closeDialog();
                    services.messages().send(viewer, services.homes().go(viewer).messageKey());
                }));
        boolean maySetHome = unlocked && guild.may(me, GuildPermission.SET_HOME);
        doors.add(Door.when(maySetHome, "<white>Set the guild home here", "<gray>Moves it to where you stand."
                        + "\n\n<dark_gray>» <white>Click to set it",
                !unlocked ? "Unlocked at level " + services.homes().unlockedAt() + "." : "Your rank cannot set it.",
                () -> {
                    Location at = viewer.getLocation();
                    IGuildsScreen.off(services, viewer, () -> services.homes().set(viewer, at), outcome -> {
                        services.messages().send(viewer, outcome.messageKey());
                        open();
                    });
                }));
        doors.add(Door.when(guild.may(me, GuildPermission.VIEW_LOG), "<white>Log",
                "<gray>Everything that happened, newest first.\n\n<dark_gray>» <white>Click to read it",
                "Your rank cannot read the log.", () -> new LogMenu(services, viewer, here, guild.id(), false).open()));

        doors.add(Door.open("<white>The guild's own choices", "<gray>Name, tag, colour, emblem, message of the day,"
                        + " who may join, friendly fire.\n<dark_gray>Those your rank may change can be changed there."
                        + "\n\n<dark_gray>» <white>Click to see them",
                () -> new GuildSettingsMenu(services, viewer, here).open()));
        doors.add(Door.open("<white>Level and contracts", "<gray>Level <white>" + level + "<gray>, what earns"
                        + " experience and this week's contracts.\n\n<dark_gray>» <white>Click to see it all",
                () -> new LevelMenu(services, viewer, here).open()));
        doors.add(Door.open("<white>Perks and vault", "<gray><white>" + services.perks().unspent(guild)
                        + "<gray> points to spend.\n<dark_gray>Perks, prestige and the shared vault."
                        + "\n\n<dark_gray>» <white>Click to see them",
                () -> new PerksMenu(services, viewer, here).open()));
        doors.add(Door.open("<white>Enchantments", "<gray>Unlock Rain's enchantments for the whole guild from its"
                        + " bank.\n\n<dark_gray>» <white>Click to see which it has",
                () -> new GuildEnchantsMenu(services, viewer, here).open()));

        List<String> leaving = guild.size() == 1
                ? List.of("<gray>You are the last member, so the guild is disbanded.")
                : guild.leader().equals(me)
                ? List.of("<gray>You lead it: hand the guild to somebody else first.")
                : List.of("<gray>Your rank and what you put in the bank stay with the guild.");
        return page(viewer, guild.emblem().prefix() + guild.name() + " [" + guild.tag() + "]",
                () -> Icons.of(guild.colour().bannerMaterial(), "<white>" + escaped(guild.name())), card(guild, level, rank),
                doors, leaving, () -> IGuildsScreen.say(services, viewer, () -> services.guilds().leave(me, services.bank())),
                this::open);
    }

    private DialogPage noGuildPage() {
        Menu here = returning(services, viewer);
        String price = services.bank().format(services.guilds().foundingPrice().minor());
        boolean mayFound = viewer.hasPermission(PermissionNodes.FOUND);
        boolean free = viewer.hasPermission(PermissionNodes.FOUND_FREE);
        long waiting = free ? 0 : services.guilds().minutesStillNeeded(viewer.getUniqueId());
        String cost = free ? "<light_purple>Free for you, as a creator."
                : services.guilds().foundingPrice().isPositive() ? "<gray>Costs <white>" + price + "<gray>." : "<gray>Free.";
        List<Door> doors = new ArrayList<>();
        doors.add(Door.when(mayFound && waiting <= 0, "<white>Found a guild", cost + "\n<dark_gray>"
                        + services.guilds().names().describe() + "."
                        + "\n\n<dark_gray>» <white>Click to type its name, then its tag",
                !mayFound ? "Founding guilds is not for you on this server."
                        : "Play " + hoursAndMinutes(waiting) + " more first.",
                // Two questions in the game's window, one after the other, each checked by the same rule the command
                // uses; a founded guild's window is the answer, a cancel leaves no guild.
                () -> IGuildsScreen.askInWindow(services, viewer, null, "The guild's name", "", 32,
                        name -> IGuildsScreen.askInWindow(services, viewer, null, "Its tag, e.g. ELF", "", 10,
                                tag -> IGuildsScreen.off(services, viewer,
                                        () -> services.guilds().found(viewer.getUniqueId(), name, tag, free), outcome -> {
                                            services.messages().send(viewer, outcome.messageKey());
                                            open();
                                        })))));
        doors.add(Door.open("<white>Every guild", "<gray>" + services.book().count() + " on this server."
                        + "\n<dark_gray>Join one or ask to.\n\n<dark_gray>» <white>Click to look through them",
                () -> new BrowseMenu(services, viewer, here).open()));
        int invitations = services.guilds().invitationsOf(viewer.getUniqueId()).size();
        doors.add(Door.open("<white>Your invitations", "<gray>" + invitations + " waiting."
                        + "\n\n<dark_gray>» <white>Click to answer them",
                () -> new InvitationsMenu(services, viewer, here).open()));
        return noGuild(viewer, doors);
    }

    private String card(Guild guild, int level, Rank rank) {
        LevelRule levels = services.guilds().levels();
        List<String> lines = new ArrayList<>();
        lines.add("<white><bold>" + escaped(guild.emblem().prefix() + guild.name()) + "</bold> <gray>[" + guild.tag() + "]");
        lines.add("<gray>Led by <white>" + escaped(services.notifier().name(guild.leader())));
        lines.add("<gray>Level <white>" + level + (levels.isTop(level) ? " <gold>(top)" : ""));
        if (!levels.isTop(level)) {
            lines.add(bar(levels.progress(guild.xp())) + " <dark_gray>" + guild.xp() + "/" + levels.toReach(level + 1));
        }
        lines.add("<gray>Joining: <white>" + guild.joinMode().title() + "<gray> · Your rank: <white>" + escaped(rank.name()));
        if (!guild.motd().isBlank()) {
            lines.add("<gray>“" + escaped(guild.motd()) + "”");
        }
        return String.join("\n", lines);
    }

    static String bar(double progress) {
        int filled = (int) Math.round(progress * 20);
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(20 - filled);
    }

    /** A guild's or player's own words, safe inside markup. */
    static String escaped(String text) {
        return de.raindancer.core.ui.text.Text.literal(text);
    }

    private static String hoursAndMinutes(long minutes) {
        return minutes >= 60 ? (minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min")
                : minutes + " min";
    }

    @Override
    public String describe() {
        return "a guild's own window: members, ranks, bank, home, relations, log and choices";
    }
}
