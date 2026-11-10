package de.raindancer.modules.guilds.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.guilds.GuildsServices;
import de.raindancer.modules.guilds.util.PermissionNodes;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** For somebody in no guild: found one, look at those there are, answer invitations. */
public final class NoGuildMenu extends Menu implements IGuildsScreen {

    private final GuildsServices services;

    public NoGuildMenu(GuildsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return Component.text("Guilds", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Guilds";
    }

    @Override
    protected void render() {
        if (services.guilds().guildOf(viewer.getUniqueId()).isPresent()) {
            band(MenuLayout.WHO, 4, Icons.of(Material.LIME_BANNER, "<green>You are in a guild now",
                    "", "<gray>Click to open its page."), click -> new GuildMenu(services, viewer, null).open());
            return;
        }
        String price = services.bank().format(services.guilds().foundingPrice().minor());
        boolean mayFound = viewer.hasPermission(PermissionNodes.FOUND);
        boolean free = viewer.hasPermission(PermissionNodes.FOUND_FREE);
        long waiting = free ? 0 : services.guilds().minutesStillNeeded(viewer.getUniqueId());
        String cost = free ? "<light_purple>Free for you, as a creator."
                : services.guilds().foundingPrice().isPositive() ? "<gray>Costs <white>" + price + "<gray>." : "<gray>Free.";
        band(MenuLayout.WHO, 2, mayFound, Icons.of(Material.BEACON, "<white>Found a guild",
                        waiting > 0 ? cost + " <red>Play <white>" + hoursAndMinutes(waiting) + "<red> more first." : cost,
                        "<dark_gray>" + services.guilds().names().describe() + ".",
                        "", "<gray>Click to type its name, then its tag:", "<gray><white>The Elves<gray>, then <white>ELF"),
                "Founding guilds is not for you on this server.",
                // Two questions in the game's dialog, one after the other, each checked by the same rule the command
                // uses; no page to come back to — a founded guild's page is the answer, a cancel leaves no guild.
                click -> IGuildsScreen.askInWindow(services, viewer, null, "The guild's name", "", 32,
                        name -> IGuildsScreen.askInWindow(services, viewer, null, "Its tag, e.g. ELF", "", 10,
                                tag -> {
                                    IGuildsScreen.say(services, viewer, () -> services.guilds().found(viewer.getUniqueId(), name, tag, free));
                                    GuildMenu.forViewer(services, viewer).open();
                                })));
        band(MenuLayout.WHO, 4, Icons.of(Material.COMPASS, "<white>Every guild",
                        "<gray>" + services.book().count() + " on this server.",
                        "", "<gray>Click to look through them and join", "<gray>or apply to one."),
                click -> new BrowseMenu(services, viewer, this).open());
        int invitations = services.guilds().invitationsOf(viewer.getUniqueId()).size();
        band(MenuLayout.WHO, 6, Icons.of(invitations > 0 ? Material.FILLED_MAP : Material.MAP, "<white>Your invitations",
                        "<gray>" + invitations + " waiting.", "", "<gray>Click to answer them."),
                click -> new InvitationsMenu(services, viewer, this).open());
    }

    @Override
    protected List<String> helpLines() {
        return List.of("You are in no guild yet.",
                "Found one of your own, look at the guilds there are",
                "and ask to join one, or answer an invitation.");
    }
    @Override
    public String describe() {
        return "the guild page for somebody in no guild";
    }

    private static String hoursAndMinutes(long minutes) {
        return minutes >= 60 ? (minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min") : minutes + " min";
    }
}
