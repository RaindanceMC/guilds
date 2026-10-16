package de.raindancer.modules.guilds.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.guilds.model.GuildPermission;
import de.raindancer.modules.guilds.model.Rank;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Gives the officers of guilds founded before 0.18.0 "Place attendants" — once, as new guilds' officers have it from
 * the start. After that it is the rank's permission like any other: a leader who takes it away is obeyed.
 * Runs before the book loads, so the book reads the new permission.
 */
public final class NpcPermissionMigration {

    static final String SOURCE = "place-npcs-0.18";

    private NpcPermissionMigration() {
    }

    /** @return ranks given it; 0 when it ran before or the database is not writable */
    public static int run(Database database, LongSupplier clock) {
        AtomicInteger given = new AtomicInteger();
        String key = GuildPermission.PLACE_NPCS.key();
        boolean written = database.write(connection -> {
            try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM guild_migration WHERE source = ?")) {
                query.setString(1, SOURCE);
                try (ResultSet rows = query.executeQuery()) {
                    if (rows.next()) {
                        return;
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE rank SET permissions = CASE WHEN permissions = '' THEN ? ELSE permissions || ',' || ? END
                    WHERE id = ? AND (',' || permissions || ',') NOT LIKE ?""")) {
                update.setString(1, key);
                update.setString(2, key);
                update.setString(3, Rank.OFFICER);
                update.setString(4, "%," + key + ",%");
                given.addAndGet(update.executeUpdate());
            }
            try (PreparedStatement mark = connection.prepareStatement(
                    "INSERT INTO guild_migration (source, at) VALUES (?, ?) ON CONFLICT (source) DO NOTHING")) {
                mark.setString(1, SOURCE);
                mark.setLong(2, clock.getAsLong());
                mark.executeUpdate();
            }
        });
        return written ? given.get() : 0;
    }
}
