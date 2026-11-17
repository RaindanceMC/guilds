package de.raindancer.modules.guilds.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.platform.network.NetworkBus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which of Rain's enchantments each guild has unlocked — for the network. Reads come from memory, which every
 * server keeps current from the network bus; unlocking is a change from "locked" that only one write can make, so
 * it is announced once.
 *
 * <p>The table and the bus topic keep their names from the time unlocking cost shards: renaming them would only
 * strand the unlocks guilds already have, and servers of the network that are not updated yet.
 */
public final class EnchantBook {

    public static final String TOPIC = "guild-shards";

    private final Database database;
    private final Map<String, Set<String>> guilds = new ConcurrentHashMap<>();
    private volatile NetworkBus bus = NetworkBus.NONE;

    public EnchantBook(Database database) {
        this.database = database;
    }

    /** @return false when it could not be read */
    public boolean load() {
        Optional<Map<String, Set<String>>> read = database.read(connection -> read(connection, null));
        read.ifPresent(all -> {
            guilds.clear();
            all.forEach((guild, unlocked) -> guilds.put(guild, ConcurrentHashMap.newKeySet()));
            all.forEach((guild, unlocked) -> guilds.get(guild).addAll(unlocked));
        });
        return read.isPresent();
    }

    public NetworkBus.Subscription connect(NetworkBus network) {
        this.bus = network == null ? NetworkBus.NONE : network;
        NetworkBus.Subscription changes = bus.subscribe(TOPIC, event -> refresh(event.key()));
        NetworkBus.Subscription resync = bus.onResync(this::load);
        return () -> {
            changes.close();
            resync.close();
        };
    }

    private void refresh(String guild) {
        database.read(connection -> read(connection, guild)).ifPresent(read -> {
            Set<String> now = ConcurrentHashMap.newKeySet();
            now.addAll(read.getOrDefault(guild, Set.of()));
            guilds.put(guild, now);
        });
    }

    private static Map<String, Set<String>> read(Connection connection, String only) throws SQLException {
        Map<String, Set<String>> all = new HashMap<>();
        String sql = "SELECT guild, enchantment FROM guild_shard WHERE unlocked = 1" + (only == null ? "" : " AND guild = ?");
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            if (only != null) {
                query.setString(1, only);
            }
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    all.computeIfAbsent(rows.getString(1), id -> new HashSet<>()).add(rows.getString(2));
                }
            }
        }
        return all;
    }

    public boolean unlocked(String guild, String enchantment) {
        return guilds.getOrDefault(guild, Set.of()).contains(enchantment);
    }

    /**
     * Unlocks an enchantment for a guild. Writes the database, so off the server's threads.
     *
     * @return whether this call did it (false: it was unlocked already, by another server maybe); empty when the
     *         database could not be written
     */
    public Optional<Boolean> unlock(String guild, String enchantment) {
        boolean[] changed = new boolean[1];
        boolean written = database.write(connection -> {
            try (PreparedStatement unlock = connection.prepareStatement("""
                    INSERT INTO guild_shard (guild, enchantment, paid, unlocked) VALUES (?, ?, 0, 1)
                    ON CONFLICT (guild, enchantment) DO UPDATE SET unlocked = 1 WHERE guild_shard.unlocked = 0""")) {
                unlock.setString(1, guild);
                unlock.setString(2, enchantment);
                changed[0] = unlock.executeUpdate() == 1;
            }
        });
        if (!written) {
            return Optional.empty();
        }
        guilds.computeIfAbsent(guild, id -> ConcurrentHashMap.newKeySet()).add(enchantment);
        if (changed[0]) {
            bus.publish(TOPIC, guild);
        }
        return Optional.of(changed[0]);
    }

    /** A disbanded guild's unlocks go with it. */
    public void forget(String guild) {
        database.write(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM guild_shard WHERE guild = ?")) {
                delete.setString(1, guild);
                delete.executeUpdate();
            }
        });
        guilds.remove(guild);
        bus.publish(TOPIC, guild);
    }
}
