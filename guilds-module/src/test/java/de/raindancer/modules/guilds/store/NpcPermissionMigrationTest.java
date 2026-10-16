package de.raindancer.modules.guilds.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.guilds.model.Fixtures;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildPermission;
import de.raindancer.modules.guilds.model.Rank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
class NpcPermissionMigrationTest {

    @TempDir
    Path folder;
    private Database database;

    @BeforeEach
    void start() {
        database = TestDatabases.open(folder.resolve("guilds.db"), GuildsDatabase.SCHEMA);
    }

    @AfterEach
    void stop() {
        database.close();
    }

    private GuildBook book() {
        GuildBook book = new GuildBook(database, problem -> { });
        book.load();
        return book;
    }

    private static Rank officer(Guild guild) {
        return guild.rank(Rank.OFFICER).orElseThrow();
    }

    @Test
    @DisplayName("officers of guilds from before 0.18 may place attendants, once; a leader who takes it away later is obeyed")
    void officersOnce() {
        GuildBook first = book();
        Guild guild = Fixtures.guild("Elves");
        guild = guild.withRank(officer(guild).toggled(GuildPermission.PLACE_NPCS));
        first.create(guild);
        first.flush();
        assertThat(officer(book().guild(guild.id().value()).orElseThrow()).permissions())
                .doesNotContain(GuildPermission.PLACE_NPCS);

        assertThat(NpcPermissionMigration.run(database, () -> 7)).isEqualTo(1);
        Guild after = book().guild(guild.id().value()).orElseThrow();
        assertThat(officer(after).permissions()).contains(GuildPermission.PLACE_NPCS, GuildPermission.INVITE);
        assertThat(after.rank(Rank.MEMBER).orElseThrow().permissions()).doesNotContain(GuildPermission.PLACE_NPCS);

        GuildBook again = book();
        again.update(guild.id(), now -> now.withRank(officer(now).toggled(GuildPermission.PLACE_NPCS)));
        again.flush();
        assertThat(NpcPermissionMigration.run(database, () -> 8)).as("only ever once").isZero();
        assertThat(officer(book().guild(guild.id().value()).orElseThrow()).permissions())
                .doesNotContain(GuildPermission.PLACE_NPCS);
    }
}
