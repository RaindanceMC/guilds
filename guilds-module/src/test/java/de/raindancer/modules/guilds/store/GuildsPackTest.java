package de.raindancer.modules.guilds.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuildsPackTest {

    @Test
    @DisplayName("the pack's file list has its pack.mcmeta, without which Core leaves the whole pack out")
    void listsTheMcmeta() {
        assertThat(GuildsPack.files()).contains("pack.mcmeta");
    }

    @Test
    @DisplayName("every file on the list is in the jar")
    void everyFileIsThere() {
        for (String file : GuildsPack.files()) {
            assertThat(GuildsPack.class.getResource(GuildsPack.ROOT + file)).as(file).isNotNull();
        }
    }
}
