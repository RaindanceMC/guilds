package de.raindancer.modules.guilds.rules;

import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.guilds.model.GuildOutcome;
import de.raindancer.modules.guilds.model.Perk;
import de.raindancer.modules.guilds.model.Standing;
import de.raindancer.modules.guilds.model.XpSource;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Perk points and the guild skill tree: where points come from, what a node may be taken for, and what the nodes
 * taken add up to.
 *
 * <p>Points come from levels ever gained (a prestige keeps them), from each prestige, and from each weekly crown.
 * A rank costs its node's points and needs the guild to be at its level <em>now</em> — so a guild that prestiged
 * keeps its nodes but takes no new high ranks until it has climbed back. A level past the server's top level counts
 * as the top level, so a server with a low top can still reach every capstone.
 */
public final class PerkRule implements IGuildsRule {

    private final SkillTree tree;
    private final int pointsPerPrestige;
    private final int pointsPerCrown;
    private final Duration respecEvery;

    public PerkRule(SkillTree tree, int pointsPerPrestige, int pointsPerCrown, Duration respecEvery) {
        this.tree = tree;
        this.pointsPerPrestige = Math.max(0, pointsPerPrestige);
        this.pointsPerCrown = Math.max(0, pointsPerCrown);
        this.respecEvery = respecEvery == null || respecEvery.isNegative() ? Duration.ZERO : respecEvery;
    }

    @Override
    public String describe() {
        return "a skill tree rank costs its node's points and needs its level; points come from levels, prestige ("
                + pointsPerPrestige + ") and crowns (" + pointsPerCrown + ")";
    }

    public SkillTree tree() {
        return tree;
    }

    public int earned(Standing standing) {
        long total = (long) standing.levelPoints() + (long) standing.prestige() * pointsPerPrestige
                + (long) standing.crowns() * pointsPerCrown;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int unspent(Standing standing) {
        return Math.max(0, earned(standing) - tree.spent(standing.nodes()));
    }

    /** The guild level rank {@code rank} of {@code node} needs on a server whose top level is {@code top}. */
    public int levelFor(TreeNode node, int rank, int top) {
        return Math.min(node.levelFor(rank), Math.max(1, top));
    }

    /** Whether the next rank of the node {@code id} may be taken by a guild at {@code level}. */
    public GuildOutcome mayRaise(Standing standing, String id, int level, int top) {
        Optional<TreeNode> node = tree.node(id);
        if (node.isEmpty()) {
            return GuildOutcome.PERK_UNKNOWN;
        }
        // At the server's top level every level a node asks is reached, however high the tree puts it.
        int measured = level >= Math.max(1, top) ? Integer.MAX_VALUE : level;
        return tree.refusal(standing.nodes(), node.get(), measured, unspent(standing)).map(why -> switch (why) {
            case MAXED -> GuildOutcome.PERK_MAXED;
            case BARRED -> GuildOutcome.PERK_BARRED;
            case NEEDS -> GuildOutcome.PERK_LOCKED;
            case LEVEL -> GuildOutcome.LEVEL_TOO_LOW;
            case NO_POINTS -> GuildOutcome.NO_PERK_POINTS;
        }).orElse(GuildOutcome.SUCCESS);
    }

    public GuildOutcome mayRespec(Standing standing, long now) {
        if (standing.spent() == 0) {
            return GuildOutcome.NOTHING_TO_RESPEC;
        }
        boolean waited = standing.respecAt() <= 0 || now - standing.respecAt() >= respecEvery.toMillis();
        return waited ? GuildOutcome.SUCCESS : GuildOutcome.ON_COOLDOWN;
    }

    public long respecAgainAt(Standing standing) {
        return standing.respecAt() <= 0 ? 0 : standing.respecAt() + respecEvery.toMillis();
    }

    // ------------------------------------------------------------------ what was taken does

    /** What all of the guild's ranks of {@code perk} add up to, to its cap. */
    public double value(Standing standing, Perk perk) {
        double sum = tree.effects(standing.nodes()).getOrDefault(perk.id(), 0.0);
        return perk.cap() > 0 ? Math.min(sum, perk.cap()) : sum;
    }

    private int whole(Standing standing, Perk perk) {
        return (int) Math.max(0, Math.round(value(standing, perk)));
    }

    public int vaultSlots(Standing standing) {
        return 9 * whole(standing, Perk.VAULT_ROWS);
    }

    public int extraMembers(Standing standing) {
        return whole(standing, Perk.MEMBERS);
    }

    public int extraAllies(Standing standing) {
        return whole(standing, Perk.ALLIES);
    }

    public int extraContracts(Standing standing) {
        return whole(standing, Perk.CONTRACTS);
    }

    public int contractRewardPercent(Standing standing) {
        return whole(standing, Perk.CONTRACT_REWARD);
    }

    public int vanillaXpPercent(Standing standing) {
        return whole(standing, Perk.VANILLA_XP);
    }

    /** The guild-experience bonus from nodes and prestige, in percent. */
    public int guildXpPercent(Standing standing, int percentPerPrestige) {
        return whole(standing, Perk.GUILD_XP) + Math.max(0, percentPerPrestige) * standing.prestige();
    }

    /** The bonus for experience from one source, in percent. */
    public int sourcePercent(Standing standing, XpSource source) {
        int percent = 0;
        for (Perk perk : Perk.values()) {
            if (perk.source() == source) {
                percent += whole(standing, perk);
            }
        }
        return percent;
    }

    /** The bonus while a guildmate is near, in percent. */
    public int togetherPercent(Standing standing) {
        return whole(standing, Perk.TOGETHER);
    }

    /** What is left of the home's warm-up and wait, in percent: 100, 50, then 0. */
    public int homeTravelPercent(Standing standing) {
        return Math.max(0, 100 - whole(standing, Perk.HOME_TRAVEL));
    }

    public int keepLevelsPercent(Standing standing) {
        return whole(standing, Perk.KEEP_LEVELS);
    }

    public int hungerSavePercent(Standing standing) {
        return whole(standing, Perk.HUNGER_SAVE);
    }

    public int fallDamagePercent(Standing standing) {
        return whole(standing, Perk.FALL_DAMAGE);
    }

    /**
     * The potions a member should have, by the game's key, as amplifiers (0 is level I). Levels of the same potion
     * add up — always-on and near-a-guildmate alike — to the highest cap among them.
     *
     * @param rallied whether a guildmate is near
     */
    public Map<String, Integer> potions(Standing standing, boolean rallied) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Integer> caps = new LinkedHashMap<>();
        Map<String, Double> effects = tree.effects(standing.nodes());
        for (Perk perk : Perk.values()) {
            if (perk.potion() == null || (perk.rally() && !rallied)) {
                continue;
            }
            int level = (int) Math.round(effects.getOrDefault(perk.id(), 0.0));
            if (level <= 0) {
                continue;
            }
            levels.merge(perk.potion(), level, Integer::sum);
            caps.merge(perk.potion(), perk.cap(), Math::max);
        }
        Map<String, Integer> amplifiers = new LinkedHashMap<>();
        levels.forEach((potion, level) -> amplifiers.put(potion, Math.min(level, caps.get(potion)) - 1));
        return amplifiers;
    }

    /** "★", "★2", … for a guild that prestiged; empty for one that did not. */
    public static String stars(int prestige) {
        return prestige <= 0 ? "" : prestige == 1 ? "★" : "★" + prestige;
    }
}
