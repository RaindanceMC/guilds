package de.raindancer.modules.guilds.rules;

import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeGrid;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.guilds.model.GuildTree;
import de.raindancer.modules.guilds.model.Perk;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GuildTreeTest {

    private final SkillTree tree = GuildTree.shipped();

    @Test
    @DisplayName("five branches of fifteen nodes, filling their page, one root and one capstone each")
    void shape() {
        assertThat(tree.branches()).hasSize(5);
        for (var branch : tree.branches()) {
            List<TreeNode> nodes = tree.nodesOf(branch.id());
            assertThat(nodes).as(branch.id()).hasSize(15);
            assertThat(nodes.stream().filter(node -> node.requires().isEmpty())).as(branch.id() + " roots").hasSize(1);
            assertThat(nodes.stream().filter(TreeNode::capstone)).as(branch.id() + " capstones").hasSize(1);
            assertThat(nodes.stream().filter(TreeNode::choice)).as(branch.id() + " choices").hasSize(2);
        }
    }

    @Test
    @DisplayName("no two lines of a branch share a slot")
    void linesNeverCross() {
        for (var branch : tree.branches()) {
            Set<Integer> used = new HashSet<>();
            for (TreeNode node : tree.nodesOf(branch.id())) {
                for (String needed : node.requires()) {
                    TreeNode other = tree.node(needed).orElseThrow();
                    var link = TreeGrid.link(other.column(), other.row(), node.column(), node.row());
                    assertThat(used.add(link.slotRow() * 9 + link.slotColumn()))
                            .as(branch.id() + ": " + needed + " to " + node.id()).isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("the eight perks of before the tree are nodes of the same id, ranks, cost and levels")
    void oldPerksCarryOver() {
        record Old(String id, int ranks, int first, int step) {
        }
        for (Old old : List.of(new Old("vault", 6, 2, 3), new Old("haste", 2, 4, 8), new Old("luck", 2, 6, 8),
                new Old("scholar", 3, 3, 4), new Old("prosperous", 3, 5, 5), new Old("recruiter", 3, 4, 4),
                new Old("diplomat", 2, 7, 6), new Old("pathfinder", 2, 4, 6))) {
            TreeNode node = tree.node(old.id()).orElseThrow(() -> new AssertionError(old.id()));
            assertThat(node.ranks()).as(old.id()).isEqualTo(old.ranks());
            assertThat(node.cost()).as(old.id()).isEqualTo(1);
            assertThat(node.levelFor(1)).as(old.id()).isEqualTo(old.first());
            assertThat(node.levelFor(old.ranks())).as(old.id()).isEqualTo(old.first() + (old.ranks() - 1) * old.step());
        }
    }

    @Test
    @DisplayName("every node can be had at the default top level; capstones need it")
    void levels() {
        for (TreeNode node : tree.nodes()) {
            assertThat(node.levelFor(node.ranks())).as(node.id()).isLessThanOrEqualTo(25);
            if (node.capstone()) {
                assertThat(node.level()).as(node.id()).isEqualTo(25);
            }
        }
    }

    @Test
    @DisplayName("every effect is used by some node, every icon is a real item, every node does something")
    void everythingUsed() {
        Set<String> used = new HashSet<>();
        for (TreeNode node : tree.nodes()) {
            assertThat(node.effects()).as(node.id()).isNotEmpty();
            used.addAll(node.effects().keySet());
            assertThat(Material.matchMaterial(node.icon())).as(node.id() + " icon").isNotNull();
        }
        for (var branch : tree.branches()) {
            assertThat(Material.matchMaterial(branch.icon())).as(branch.id() + " icon").isNotNull();
        }
        for (Perk perk : Perk.values()) {
            assertThat(used).as(perk.id()).contains(perk.id());
        }
    }

    @Test
    @DisplayName("every effect reads as a sentence, a negative one too")
    void sentences() {
        assertThat(Perk.MEMBERS.describe(2)).isEqualTo("Room for 2 more members");
        assertThat(Perk.HASTE.describe(1)).isEqualTo("Members have Haste I");
        assertThat(Perk.RALLY_STRENGTH.describe(1)).isEqualTo("Strength I near a guildmate");
        assertThat(Perk.XP_MINE.describe(10)).isEqualTo("10% more guild experience from mining");
        for (Perk perk : Perk.values()) {
            assertThat(perk.describe(1)).as(perk.id()).doesNotContain("%s").doesNotContain("%%");
        }
    }

    @Test
    @DisplayName("a whole branch costs more than a guild earns climbing to the top once: prestige fills the rest")
    void branchesAreDear() {
        for (var branch : tree.branches()) {
            Map<String, Integer> all = new java.util.HashMap<>();
            tree.nodesOf(branch.id()).forEach(node -> all.put(node.id(), node.ranks()));
            int cost = tree.spent(all);
            assertThat(cost).as(branch.id()).isBetween(25, 45);
        }
    }

    @Test
    @DisplayName("the pack lists the tree's fonts and every file it lists is in the jar")
    void pack() {
        var files = de.raindancer.modules.guilds.store.GuildsPack.files();
        assertThat(files).contains("assets/rainsguilds/font/tree.json", "assets/rainsguilds/font/tree_scene.json");
        for (String file : files) {
            assertThat(getClass().getResource("/de/raindancer/modules/guilds/pack/" + file)).as(file).isNotNull();
        }
        for (var branch : tree.branches()) {
            assertThat(de.raindancer.modules.guilds.store.GuildsPack.treeBackdrop(branch.id())).as(branch.id()).isNotNull();
        }
    }
}
