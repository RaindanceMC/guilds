package de.raindancer.modules.guilds;

import de.raindancer.core.social.desk.ServiceDesk;
import de.raindancer.modules.guilds.model.Fixtures;
import de.raindancer.modules.guilds.model.Guild;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("attendants a guild places itself")
class GuildNpcsTest {

    private final Guild guild = Fixtures.guild("Dawn");
    private final String id = guild.id().value();

    @Test
    @DisplayName("the leader and the officers may place them, members and recruits may not")
    void who() {
        assertThat(GuildNpcs.mayPlace(guild, Fixtures.LEADER)).isTrue();
        assertThat(GuildNpcs.mayPlace(guild, Fixtures.OFFICER)).isTrue();
        assertThat(GuildNpcs.mayPlace(guild, Fixtures.MEMBER)).isFalse();
        assertThat(GuildNpcs.mayPlace(guild, Fixtures.RECRUIT)).isFalse();
        assertThat(GuildNpcs.mayPlace(guild, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("one of each kind per guild; placing it elsewhere means taking the old one away first")
    void howMany() {
        GuildNpcs npcs = new GuildNpcs();
        UUID first = UUID.randomUUID();
        assertThat(npcs.placed(id, ServiceDesk.GUILD_BANK)).isEmpty();
        npcs.record(id, ServiceDesk.GUILD_BANK, new GuildNpcs.Placed(first, "world", 1, 64, 2));
        assertThat(npcs.placed(id, ServiceDesk.GUILD_BANK)).isPresent();
        assertThat(npcs.placed(id, ServiceDesk.GUILD_VAULT)).isEmpty();
        assertThat(npcs.isCurrent(id, ServiceDesk.GUILD_BANK, first)).isTrue();
        assertThat(npcs.forget(id, ServiceDesk.GUILD_BANK)).map(GuildNpcs.Placed::entity).contains(first);
        assertThat(npcs.isCurrent(id, ServiceDesk.GUILD_BANK, first)).as("an attendant no longer on record goes").isFalse();
    }

    @Test
    @DisplayName("what is on record is written as lines and read back")
    void kept() {
        GuildNpcs npcs = new GuildNpcs();
        UUID entity = UUID.randomUUID();
        npcs.record(id, ServiceDesk.GUILD_ENCHANTS, new GuildNpcs.Placed(entity, "world", -3.5, 70, 8.5));
        GuildNpcs again = new GuildNpcs();
        again.read(npcs.lines());
        assertThat(again.placed(id, ServiceDesk.GUILD_ENCHANTS)).contains(new GuildNpcs.Placed(entity, "world", -3.5, 70, 8.5));
        assertThat(again.forgetGuild(id)).hasSize(1);
        assertThat(again.lines()).isEmpty();
    }
}
