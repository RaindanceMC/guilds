package de.raindancer.modules.guilds.rules;

import de.raindancer.modules.guilds.model.GuildOutcome;
import de.raindancer.modules.guilds.model.GuildTree;
import de.raindancer.modules.guilds.model.Perk;
import de.raindancer.modules.guilds.model.Standing;
import de.raindancer.modules.guilds.model.XpSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PerkRulesTest {

    private static final int TOP = 25;
    private final PerkRule rule = new PerkRule(GuildTree.shipped(), 3, 1, Duration.ofDays(7));

    private static Standing with(Map<String, Integer> nodes) {
        return new Standing(0, 0, 0, nodes, 0);
    }

    @Nested
    @DisplayName("points")
    class Points {

        @Test
        @DisplayName("levels, prestige and crowns all give points; ranks spend their node's cost")
        void earnedAndSpent() {
            Standing standing = new Standing(4, 2, 1, Map.of("vault", 2), 0);
            assertThat(rule.earned(standing)).isEqualTo(4 + 6 + 1);
            assertThat(rule.unspent(standing)).isEqualTo(9);
            assertThat(rule.unspent(new Standing(20, 0, 0, Map.of("charter", 1), 0))).as("a choice costs two").isEqualTo(18);
            assertThat(rule.unspent(new Standing(1, 0, 0, Map.of("vault", 3), 0))).as("never below zero").isZero();
        }

        @Test
        @DisplayName("a rank stored above its node's top counts as the top; a node no longer in the tree as nothing")
        void clamped() {
            assertThat(rule.value(with(Map.of("haste", 9)), Perk.HASTE)).isEqualTo(2);
            assertThat(rule.unspent(new Standing(5, 0, 0, Map.of("gone", 3), 0))).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("buying")
    class Buying {

        @Test
        @DisplayName("a rank needs a point, its level, a neighbour taken, and room at the top")
        void raise() {
            assertThat(rule.mayRaise(Standing.NONE, "vault", 10, TOP)).as("needs the recruiter first")
                    .isEqualTo(GuildOutcome.PERK_LOCKED);
            Standing rich = new Standing(40, 0, 0, Map.of("recruiter", 1), 0);
            assertThat(rule.mayRaise(Standing.NONE.withLevelPoints(0), "recruiter", 10, TOP)).isEqualTo(GuildOutcome.NO_PERK_POINTS);
            assertThat(rule.mayRaise(rich, "vault", 1, TOP)).as("vault I needs level 2").isEqualTo(GuildOutcome.LEVEL_TOO_LOW);
            assertThat(rule.mayRaise(rich, "vault", 2, TOP)).isEqualTo(GuildOutcome.SUCCESS);
            assertThat(rule.mayRaise(rich.withRank("vault", 1), "vault", 4, TOP)).as("vault II needs level 5").isEqualTo(GuildOutcome.LEVEL_TOO_LOW);
            assertThat(rule.mayRaise(rich.withRank("haste", 2), "haste", 25, TOP)).isEqualTo(GuildOutcome.PERK_MAXED);
            assertThat(rule.mayRaise(rich, "nothing", 25, TOP)).isEqualTo(GuildOutcome.PERK_UNKNOWN);
        }

        @Test
        @DisplayName("taking one side of a choice shuts the other")
        void choice() {
            Standing standing = new Standing(40, 0, 0, Map.of("recruiter", 1, "quartermaster", 1, "town_crier", 1,
                    "charter", 1), 0);
            assertThat(rule.mayRaise(standing, "embassy", 25, TOP)).isEqualTo(GuildOutcome.PERK_BARRED);
        }

        @Test
        @DisplayName("a server whose top level is lower can still reach every node: levels past the top count as the top")
        void lowTop() {
            Standing standing = new Standing(99, 0, 0, Map.of("ledger_keepers", 1), 0);
            assertThat(rule.mayRaise(standing, "grand_hall", 10, 25)).isEqualTo(GuildOutcome.LEVEL_TOO_LOW);
            assertThat(rule.mayRaise(standing, "grand_hall", 10, 10)).isEqualTo(GuildOutcome.SUCCESS);
            assertThat(rule.levelFor(GuildTree.shipped().node("grand_hall").orElseThrow(), 1, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("points come back once a week, and only when something was bought")
        void respec() {
            long day = Duration.ofDays(1).toMillis();
            assertThat(rule.mayRespec(Standing.NONE, 0)).isEqualTo(GuildOutcome.NOTHING_TO_RESPEC);
            Standing bought = new Standing(5, 0, 0, Map.of("scholar", 1), 0);
            assertThat(rule.mayRespec(bought, 10 * day)).isEqualTo(GuildOutcome.SUCCESS);
            Standing after = bought.respecced(10 * day).withRank("scholar", 1);
            assertThat(after.nodes()).containsOnlyKeys("scholar");
            assertThat(rule.mayRespec(after, 16 * day)).isEqualTo(GuildOutcome.ON_COOLDOWN);
            assertThat(rule.mayRespec(after, 17 * day)).isEqualTo(GuildOutcome.SUCCESS);
            assertThat(rule.respecAgainAt(after)).isEqualTo(17 * day);
        }
    }

    @Nested
    @DisplayName("what the nodes do")
    class Effects {

        @Test
        @DisplayName("the old perks mean what they meant")
        void oldPerks() {
            Standing all = new Standing(0, 2, 0, Map.of("vault", 3, "haste", 2, "luck", 1, "scholar", 3,
                    "prosperous", 2, "recruiter", 3, "diplomat", 1, "pathfinder", 1), 0);
            assertThat(rule.vaultSlots(all)).isEqualTo(27);
            assertThat(rule.potions(all, false)).containsEntry("haste", 1).containsEntry("luck", 0);
            assertThat(rule.potions(Standing.NONE, true)).as("none").isEmpty();
            assertThat(rule.vanillaXpPercent(all)).isEqualTo(30);
            assertThat(rule.guildXpPercent(all, 5)).as("10% prosperous + 2 prestige × 5%").isEqualTo(20);
            assertThat(rule.extraMembers(all)).isEqualTo(6);
            assertThat(rule.extraAllies(all)).isEqualTo(1);
            assertThat(rule.homeTravelPercent(all)).isEqualTo(50);
            assertThat(rule.homeTravelPercent(with(Map.of("pathfinder", 2)))).isZero();
            assertThat(rule.homeTravelPercent(Standing.NONE)).isEqualTo(100);
        }

        @Test
        @DisplayName("the new ones: experience by source, contracts, togetherness, death, hunger and falls")
        void newOnes() {
            Standing s = with(Map.of("prospectors", 2, "deep_delvers", 1, "quartermaster", 3, "notice_board", 1,
                    "bounty_board", 1, "shoulder_to_shoulder", 2, "soul_ward", 2, "undying_lore", 1,
                    "soft_landings", 2, "mountain_goats", 1));
            assertThat(rule.sourcePercent(s, XpSource.MINE)).isEqualTo(50);
            assertThat(rule.sourcePercent(s, XpSource.FISH)).isZero();
            assertThat(rule.contractRewardPercent(s)).isEqualTo(30);
            assertThat(rule.extraContracts(s)).isEqualTo(2);
            assertThat(rule.togetherPercent(s)).isEqualTo(10);
            assertThat(rule.keepLevelsPercent(s)).as("never past 100").isEqualTo(100);
            assertThat(rule.fallDamagePercent(s)).isEqualTo(70);
            assertThat(rule.hungerSavePercent(with(Map.of("full_larders", 1, "harvest_festival", 1)))).isEqualTo(30);
        }

        @Test
        @DisplayName("potions: always, or only near a guildmate; levels of one potion add up, to its cap")
        void potions() {
            Standing s = with(Map.of("haste", 2, "deep_delvers", 1, "master_artisans", 1, "fleet_foot", 1,
                    "banner_bearers", 1, "rally", 1, "warband", 1, "sailors", 1));
            assertThat(rule.potions(s, false)).containsEntry("haste", 2).containsEntry("speed", 0)
                    .containsEntry("water_breathing", 0).doesNotContainKey("regeneration");
            assertThat(rule.potions(s, true)).as("near a guildmate: Speed II, Regeneration II")
                    .containsEntry("speed", 1).containsEntry("regeneration", 1);
        }

        @Test
        void stars() {
            assertThat(PerkRule.stars(0)).isEmpty();
            assertThat(PerkRule.stars(1)).isEqualTo("★");
            assertThat(PerkRule.stars(3)).isEqualTo("★3");
        }
    }

    @Nested
    @DisplayName("the crown")
    class Crown {

        @Test
        @DisplayName("most experience first, ties by id, nobody without experience")
        void podium() {
            CrownRule crown = new CrownRule();
            var podium = crown.podium(Map.of("elves", 500L, "dwarves", 900L, "orcs", 500L, "idle", 0L), 3);
            assertThat(podium).extracting(CrownRule.Place::guild).containsExactly("dwarves", "elves", "orcs");
            assertThat(crown.podium(Map.of("idle", 0L), 3)).isEmpty();
        }
    }
}
