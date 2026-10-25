package de.raindancer.modules.guilds.rules;

import de.raindancer.modules.guilds.model.Fixtures;
import de.raindancer.modules.guilds.model.Guild;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Guild boards: who leads by experience, by bank, by members")
class GuildBoardRuleTest {

    private final GuildBoardRule rule = new GuildBoardRule();

    private final Guild dawn = Fixtures.guild("Dawn").withXp(5_000).withBank(100);
    private final Guild dusk = Fixtures.solo("Dusk", Fixtures.player("x")).withXp(9_000).withBank(50);
    private final Guild noon = Fixtures.solo("Noon", Fixtures.player("y")).withXp(9_000).withBank(9_999);

    @Test
    @DisplayName("by experience, most first; a tie goes by name")
    void byExperience() {
        assertThat(rule.top(List.of(dawn, dusk, noon), GuildBoardRule.Board.EXPERIENCE, 10))
                .extracting(Guild::name).containsExactly("Dusk", "Noon", "Dawn");
    }

    @Test
    @DisplayName("by bank and by members, cut to the size asked")
    void byBankAndMembers() {
        assertThat(rule.top(List.of(dawn, dusk, noon), GuildBoardRule.Board.BANK, 2))
                .extracting(Guild::name).containsExactly("Noon", "Dawn");
        assertThat(rule.top(List.of(dawn, dusk, noon), GuildBoardRule.Board.MEMBERS, 1))
                .extracting(Guild::name).containsExactly("Dawn");
        assertThat(rule.top(List.of(), GuildBoardRule.Board.BANK, 5)).isEmpty();
    }

    @Test
    @DisplayName("progress to the next level as a bar of ten")
    void bar() {
        assertThat(rule.bar(0)).isEqualTo("<green><dark_gray>▮▮▮▮▮▮▮▮▮▮");
        assertThat(rule.bar(0.5)).isEqualTo("<green>▮▮▮▮▮<dark_gray>▮▮▮▮▮");
        assertThat(rule.bar(5)).isEqualTo("<green>▮▮▮▮▮▮▮▮▮▮<dark_gray>");
        assertThat(rule.bar(-1)).isEqualTo("<green><dark_gray>▮▮▮▮▮▮▮▮▮▮");
    }
}
