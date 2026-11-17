package de.raindancer.modules.guilds.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OwnGuildRuleTest {

    private final OwnGuildRule rule = new OwnGuildRule();

    @Test
    @DisplayName("a creator's guild is named after them, with a numbered name to fall back on")
    void names() {
        assertThat(rule.names("Raindancer118")).startsWith("Raindancer118's Guild", "Raindancer118's Guild 2");
        assertThat(rule.names("Raindancer118")).contains("Raindancer118");
    }

    @Test
    @DisplayName("its tag is the first letters and digits of their name in capitals, then shorter ones with a digit")
    void tags() {
        List<String> tags = rule.tags("Rain_dancer118", 5);
        assertThat(tags.getFirst()).isEqualTo("RAIND");
        assertThat(tags).contains("RAIN2", "RAIN9").doesNotHaveDuplicates();
        assertThat(tags).allMatch(tag -> tag.matches("[A-Z0-9]{1,5}"));
    }

    @Test
    @DisplayName("a short name still makes a tag; one with no letters or digits at all borrows a word")
    void oddNames() {
        assertThat(rule.tags("Bo", 5).getFirst()).isEqualTo("BO");
        assertThat(rule.tags("___", 5).getFirst()).isEqualTo("GUILD");
        assertThat(rule.tags("Raindancer", 3).getFirst()).isEqualTo("RAI");
    }
}
