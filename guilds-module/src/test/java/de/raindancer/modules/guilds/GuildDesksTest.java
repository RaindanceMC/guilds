package de.raindancer.modules.guilds;

import de.raindancer.core.content.enchant.EnchantUnlocks;
import de.raindancer.core.social.desk.ServiceDesk;
import de.raindancer.core.social.desk.ServiceDesks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("the guild's attendants")
class GuildDesksTest {

    @AfterEach
    void clear() {
        ServiceDesks.clear();
    }

    @Test
    @DisplayName("each desk wears the skins its setting names")
    void skins() {
        GuildDesks.skins(new GuildNpcSettings("mineskin:b0af33be", "mineskin:9e560906", "mineskin:c94d4f6e"));
        assertThat(ServiceDesks.skins(ServiceDesk.GUILD_BANK)).isEqualTo("mineskin:b0af33be");
        assertThat(ServiceDesks.skins(ServiceDesk.GUILD_VAULT)).isEqualTo("mineskin:9e560906");
        assertThat(ServiceDesks.skins(ServiceDesk.GUILD_ENCHANTS)).isEqualTo("mineskin:c94d4f6e");
        GuildDesks.skins(GuildNpcSettings.DEFAULTS);
        assertThat(ServiceDesks.skins(ServiceDesk.GUILD_BANK)).isEmpty();
    }

    @Test
    @DisplayName("every desk offers something while the guilds run, nothing before")
    void offers() {
        GuildDesks desks = new GuildDesks(() -> null);
        desks.offer();
        for (ServiceDesk desk : GuildDesks.DESKS) {
            assertThat(ServiceDesks.offers(desk)).hasSize(1);
            assertThat(ServiceDesks.offers(desk).getFirst().available()).isFalse();
        }
        desks.withdraw();
        assertThat(ServiceDesks.offers(ServiceDesk.GUILD_BANK)).isEmpty();
    }

    @Test
    @DisplayName("the enchanter is left out while no shards can be earned on this server; bank and vault stay")
    void enchanterNeedsShards() {
        GuildsServices running = new GuildsServices(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        GuildDesks desks = new GuildDesks(() -> running);
        desks.offer();
        BooleanSupplier off = () -> false;
        EnchantUnlocks.shardsFrom(null, off);
        try {
            assertThat(ServiceDesks.offers(ServiceDesk.GUILD_ENCHANTS).getFirst().available()).isFalse();
            assertThat(ServiceDesks.offers(ServiceDesk.GUILD_BANK).getFirst().available()).isTrue();
            assertThat(ServiceDesks.offers(ServiceDesk.GUILD_VAULT).getFirst().available()).isTrue();
        } finally {
            EnchantUnlocks.retractShards(off);
        }
        assertThat(ServiceDesks.offers(ServiceDesk.GUILD_ENCHANTS).getFirst().available()).isTrue();
    }

    @Test
    @DisplayName("staff name a desk by a short word")
    void named() {
        assertThat(GuildDesks.named("bank")).contains(ServiceDesk.GUILD_BANK);
        assertThat(GuildDesks.named("vault")).contains(ServiceDesk.GUILD_VAULT);
        assertThat(GuildDesks.named("enchants")).contains(ServiceDesk.GUILD_ENCHANTS);
        assertThat(GuildDesks.named("insurance")).isEmpty();
    }
}
