package de.raindancer.modules.guilds.screen;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.tree.NodeState;
import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeBranch;
import de.raindancer.core.ui.tree.TreeLook;
import de.raindancer.core.ui.tree.TreeMenu;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.guilds.GuildsServices;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildOutcome;
import de.raindancer.modules.guilds.model.GuildPermission;
import de.raindancer.modules.guilds.model.Perk;
import de.raindancer.modules.guilds.model.Standing;
import de.raindancer.modules.guilds.rules.PerkRule;
import de.raindancer.modules.guilds.store.GuildsPack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The guild skill tree: one page per branch, a click takes the next rank of a node for the guild. Drawn from the
 * standing as it is known — {@link de.raindancer.modules.guilds.service.PerkService#warm} reads it ahead — and a
 * rank is taken off the server's threads.
 */
public final class GuildTreeMenu extends TreeMenu implements IGuildsScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final GuildsServices services;

    public GuildTreeMenu(GuildsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    /** The tree, opened on {@code branch}'s page. */
    public static void show(GuildsServices services, Player viewer, Menu parent, int branch) {
        GuildTreeMenu menu = new GuildTreeMenu(services, viewer, parent);
        menu.turnTo(branch);
    }

    private PerkRule rule() {
        return services.perks().rule();
    }

    private SkillTree tree() {
        return rule().tree();
    }

    private Optional<Guild> guild() {
        return services.guilds().guildOf(viewer.getUniqueId());
    }

    private Standing standing() {
        return guild().map(services.perks()::standing).orElse(Standing.NONE);
    }

    @Override
    protected int pages() {
        return tree().branches().size();
    }

    @Override
    protected Page page(int index) {
        TreeBranch branch = tree().branches().get(index);
        Standing standing = standing();
        int level = guild().map(services.perks()::level).orElse(0);
        boolean mayTake = guild().map(each -> each.may(viewer.getUniqueId(), GuildPermission.PERKS)).orElse(false);
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (TreeNode node : tree().nodesOf(branch.id())) {
            NodeState state = tree().state(standing.nodes(), node);
            nodes.add(new Node(node.id(), node.column(), node.row(), state,
                    icon(standing, node, state, level, mayTake)));
            for (String needed : node.requires()) {
                edges.add(new Edge(needed, node.id()));
            }
        }
        return new Page(MINI.deserialize("<dark_gray>" + branch.title()), nodes, edges,
                GuildsPack.treeBackdrop(branch.id()), summary(standing, branch, mayTake));
    }

    @Override
    protected TreeLook look() {
        return GuildsPack.TREE_LOOK;
    }

    private ItemStack icon(Standing standing, TreeNode node, NodeState state, int level, boolean mayTake) {
        int rank = standing.rank(node.id());
        String colour = switch (state) {
            case MAXED -> "<gold>";
            case OWNED -> "<yellow>";
            case OPEN -> "<green>";
            case LOCKED -> "<gray>";
            case BARRED -> "<dark_red>";
        };
        List<String> lore = new ArrayList<>();
        String kind = node.capstone() ? "Capstone" : node.choice() ? "Choice — shuts the other side" : null;
        if (kind != null) {
            lore.add("<gold>" + kind);
        }
        int shown = Math.max(1, Math.min(rank, node.ranks()));
        node.effects().forEach((id, value) -> Perk.parse(id).ifPresent(perk ->
                lore.add("<gray>" + perk.describe(value * shown))));
        if (node.ranks() > 1 && rank < node.ranks()) {
            lore.add("<dark_gray>Each rank: " + String.join(", ", node.effects().entrySet().stream()
                    .flatMap(entry -> Perk.parse(entry.getKey()).stream()
                            .map(perk -> perk.describe(entry.getValue()).toLowerCase(Locale.ROOT)))
                    .toList()));
        }
        lore.add("");
        lore.add("<gray>Rank <white>" + rank + "<gray>/" + node.ranks() + " · <white>" + node.cost()
                + "<gray> point" + (node.cost() == 1 ? "" : "s") + " a rank");
        int top = services.perks().topLevel();
        if (rank < node.ranks()) {
            int needs = rule().levelFor(node, rank + 1, top);
            if (needs > 0) {
                lore.add("<gray>Needs guild level <white>" + needs);
            }
        }
        GuildOutcome may = mayTake ? rule().mayRaise(standing, node.id(), level, top) : GuildOutcome.NOT_ALLOWED;
        lore.add(may.isSuccess() ? "<yellow>Click to take the next rank." : "<red>" + reason(may, node, rank, top));
        ItemStack icon = Icons.of(material(node.icon()), colour + node.title()
                + (node.ranks() > 1 ? " <white>" + rank + "<gray>/" + node.ranks() : ""), lore);
        if (rank > 0) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    private String reason(GuildOutcome why, TreeNode node, int rank, int top) {
        return switch (why) {
            case PERK_MAXED -> "At its highest rank.";
            case PERK_BARRED -> "Shut: the guild took the other side of this choice.";
            case PERK_LOCKED -> "Needs a node next to it first.";
            case LEVEL_TOO_LOW -> "Needs guild level " + rule().levelFor(node, rank + 1, top) + ".";
            case NO_PERK_POINTS -> "Not enough perk points.";
            case NOT_ALLOWED -> "Your rank cannot spend points.";
            default -> "Not possible right now.";
        };
    }

    private ItemStack summary(Standing standing, TreeBranch branch, boolean mayTake) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + branch.blurb());
        lore.add("");
        lore.add("<green>Perk points to spend: <white>" + rule().unspent(standing) + "<gray> of " + rule().earned(standing));
        lore.add("<gray>A point a level up, more for each");
        lore.add("<gray>prestige and each weekly crown.");
        lore.add("<gray>Spent in this branch: <white>" + tree().spentIn(standing.nodes(), branch.id()));
        if (standing.prestige() > 0) {
            lore.add("<gold>" + PerkRule.stars(standing.prestige()) + " <gray>prestige " + standing.prestige());
        }
        if (mayTake && standing.spent() > 0) {
            lore.add("");
            lore.add("<yellow>Shift-click<gray> to unlearn the whole tree.");
        }
        return Icons.of(material(branch.icon()), "<gold>" + branch.title(), lore);
    }

    @Override
    protected void onNode(Node node, InventoryClickEvent event) {
        IGuildsScreen.off(services, viewer, () -> services.perks().raise(viewer.getUniqueId(), node.id()), outcome -> {
            if (outcome.isSuccess()) {
                services.core().effects().play(viewer.getUniqueId(), Cues.OK);
                reopen();
            } else {
                services.core().effects().play(viewer.getUniqueId(), Cues.NO);
                services.messages().send(viewer, outcome.messageKey());
            }
        });
    }

    @Override
    protected void onSummary(InventoryClickEvent event) {
        if (!event.isShiftClick() || standing().spent() == 0) {
            return;
        }
        new ConfirmScreen(services, viewer, this, "<red>Unlearn the whole skill tree?",
                List.of("<gray>Every point comes back to spend", "<gray>again; the guild loses every node",
                        "<gray>until then. Once every " + services.xp().progressSettings().respecDays() + " day(s)."),
                () -> IGuildsScreen.off(services, viewer, () -> services.perks().respec(viewer.getUniqueId()), outcome -> {
                    services.messages().send(viewer, outcome.messageKey());
                    reopen();
                })).open();
    }

    private static Material material(String id) {
        Material material = Material.matchMaterial(id);
        return material == null ? Material.PAPER : material;
    }

    @Override
    public String breadcrumb() {
        return "Skill tree";
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Five branches; the arrows below turn between them.",
                "Click a node to take its next rank for the guild.",
                "A node needs one next to it first; a choice shuts",
                "its other side. Ranks need a guild level too.",
                "",
                "Perk points: one per level up, more for each",
                "prestige and each weekly crown.",
                "Shift-click the middle button to unlearn everything.");
    }

    @Override
    public String describe() {
        return "the guild skill tree and the perk points to spend on it";
    }
}
