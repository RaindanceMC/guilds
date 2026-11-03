package de.raindancer.modules.guilds.model;

import de.raindancer.core.ui.tree.SkillTree;

/** The guild skill tree, {@code skilltree.yml} in the jar. */
public final class GuildTree {

    private static volatile SkillTree shipped;

    private GuildTree() {
    }

    public static SkillTree shipped() {
        SkillTree known = shipped;
        if (known == null) {
            known = SkillTree.resource(GuildTree.class, "/de/raindancer/modules/guilds/skilltree.yml", Perk::known);
            shipped = known;
        }
        return known;
    }
}
