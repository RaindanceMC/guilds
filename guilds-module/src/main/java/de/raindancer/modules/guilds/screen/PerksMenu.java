package de.raindancer.modules.guilds.screen;

import de.raindancer.core.platform.util.Times;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.guilds.GuildsServices;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildOutcome;
import de.raindancer.modules.guilds.model.GuildPermission;
import de.raindancer.modules.guilds.model.Standing;
import de.raindancer.modules.guilds.rules.PerkRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The guild's honours: perk points, the five branches of the skill tree (each opens {@link GuildTreeMenu} on its
 * page), the vault, taking the points back and prestige — the last two behind a confirmation.
 */
public final class PerksMenu extends Menu implements IGuildsScreen {

    private final GuildsServices services;

    public PerksMenu(GuildsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return Component.text("Perks", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Perks";
    }

    @Override
    protected void render() {
        Optional<Guild> found = services.guilds().guildOf(viewer.getUniqueId());
        if (found.isEmpty()) {
            band(MenuLayout.WHO, 4, Icons.of(Material.BARRIER, "<red>You are in no guild now"));
            return;
        }
        Guild guild = found.get();
        UUID me = viewer.getUniqueId();
        Standing standing = services.perks().standing(guild);
        PerkRule rule = services.perks().rule();
        int level = services.guilds().level(guild);
        boolean mayBuy = guild.may(me, GuildPermission.PERKS);

        List<String> header = new ArrayList<>();
        header.add("<gray>Points to spend: <white>" + rule.unspent(standing) + "<gray> of " + rule.earned(standing));
        header.add("<dark_gray>A point for every level up, more for");
        header.add("<dark_gray>each prestige and each weekly crown.");
        if (standing.prestige() > 0) {
            header.add("<gold>" + PerkRule.stars(standing.prestige()) + " <gray>prestige " + standing.prestige());
        }
        if (standing.crowns() > 0) {
            header.add("<gold>♛ <gray>crowned " + standing.crowns() + " week(s)");
        }
        var tree = rule.tree();
        header.add("");
        header.add("<yellow>Click to open the skill tree.");
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.NETHER_STAR, "<white>Perk points", header),
                click -> GuildTreeMenu.show(services, viewer, this, 0));
        for (int index = 0; index < tree.branches().size(); index++) {
            var branch = tree.branches().get(index);
            var nodes = tree.nodesOf(branch.id());
            long taken = nodes.stream().filter(node -> standing.rank(node.id()) > 0).count();
            int spent = tree.spentIn(standing.nodes(), branch.id());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + branch.blurb());
            lore.add("");
            lore.add("<gray>Nodes taken: <white>" + taken + "<gray> of " + nodes.size());
            lore.add("<gray>Points spent here: <white>" + spent);
            lore.add("");
            lore.add("<yellow>Click to open this branch.");
            Material icon = Optional.ofNullable(Material.matchMaterial(branch.icon())).orElse(Material.PAPER);
            var item = Icons.of(icon, (taken > 0 ? "<gold>" : "<white>") + branch.title(), lore);
            if (taken > 0) {
                item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            int page = index;
            cell(2, index * 2, item, click -> GuildTreeMenu.show(services, viewer, this, page));
        }

        GuildOutcome respec = mayBuy ? rule.mayRespec(standing, System.currentTimeMillis()) : GuildOutcome.NOT_ALLOWED;
        long again = rule.respecAgainAt(standing) - System.currentTimeMillis();
        toolbar(2, respec.isSuccess(), Icons.of(Material.GRINDSTONE, "<white>Take the points back",
                        "<gray>Every node goes; every point comes back", "<gray>to spend again.",
                        "<dark_gray>Once every " + services.xp().progressSettings().respecDays() + " day(s).",
                        "", "<gray>You will be asked first."),
                respec == GuildOutcome.ON_COOLDOWN ? "Again in " + Times.describe(Duration.ofMillis(Math.max(0, again))) + "."
                        : respec == GuildOutcome.NOTHING_TO_RESPEC ? "Nothing is bought yet." : "Your rank cannot spend points.",
                click -> new ConfirmScreen(services, viewer, this, "<red>Unlearn the whole skill tree?",
                        List.of("<gray>The guild loses them until the", "<gray>points are spent again."),
                        () -> IGuildsScreen.tell(services, viewer, this, () -> services.perks().respec(me))).open());
        boolean vault = services.perks().hasVault(guild);
        toolbar(6, vault, Icons.of(Material.ENDER_CHEST, "<white>The guild vault",
                        "<gray>" + services.perks().vaultSlots(guild) + " stacks.", "", "<gray>Click to open it."),
                "Take the Guild Vault node first (The Hall).", click -> GuildVaultMenu.show(services, viewer, this));

        boolean top = services.xp().levels().isTop(level);
        boolean leader = guild.leader().equals(me);
        if (top && leader) {
            int next = standing.prestige() + 1;
            danger(Icons.of(Material.BEACON, "<gold>Prestige " + PerkRule.stars(next),
                            "<gray>The guild goes back to level 1 and", "<gray>keeps every node and point.",
                            "<gray>It earns <white>" + services.xp().progressSettings().pointsPerPrestige()
                                    + "<gray> points, a star in its tag, and",
                            "<white>+" + services.xp().progressSettings().percentPerPrestige() + "%<gray> experience for good.",
                            "", "<gray>You will be asked first."),
                    click -> new ConfirmScreen(services, viewer, this, "<gold>Prestige the guild?",
                            List.of("<gray>Level 1 again — members and allies", "<gray>stay, the level's room goes until",
                                    "<gray>it is earned back."),
                            () -> IGuildsScreen.tell(services, viewer, this, () -> services.perks().prestige(me))).open());
        } else {
            toolbar(4, Icons.locked(Icons.of(Material.BEACON, "<white>Prestige",
                            "<gray>At level " + services.xp().levels().topLevel() + ", start again from 1",
                            "<gray>for a star, points and a lasting bonus."),
                    !top ? "Reach level " + services.xp().levels().topLevel() + " first." : "Only the leader decides this."),
                    click -> services.messages().send(viewer, (top ? GuildOutcome.NOT_ALLOWED : GuildOutcome.NOT_TOP_LEVEL).messageKey()));
        }
    }

    @Override
    public String describe() {
        return "the guild's perk points, its skill tree's branches, taking points back, and prestige";
    }
}
