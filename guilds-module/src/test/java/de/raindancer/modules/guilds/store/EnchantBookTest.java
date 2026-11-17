package de.raindancer.modules.guilds.store;

import de.raindancer.core.platform.network.NetworkBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
class EnchantBookTest {

    private static final String TELEKINESIS = "rainsenchants:telekinesis";

    @TempDir
    Path folder;
    private TwoServers servers;

    @BeforeEach
    void open() {
        servers = new TwoServers(folder);
    }

    @AfterEach
    void close() {
        servers.close();
    }

    private EnchantBook book(String server) {
        EnchantBook book = new EnchantBook(servers.database());
        assertThat(book.load()).isTrue();
        book.connect(servers.hub.bus(server));
        return book;
    }

    @Test
    @DisplayName("the first unlock counts, a second one changes nothing; another guild is not affected")
    void unlocks() {
        EnchantBook book = book("smp");
        assertThat(book.unlocked("g1", TELEKINESIS)).isFalse();

        assertThat(book.unlock("g1", TELEKINESIS)).contains(true);
        assertThat(book.unlocked("g1", TELEKINESIS)).isTrue();
        assertThat(book.unlocked("g2", TELEKINESIS)).as("another guild").isFalse();
        assertThat(book.unlock("g1", TELEKINESIS)).as("not twice").contains(false);
    }

    @Test
    @DisplayName("another server of the network hears of it, and a restart reads it back")
    void network() {
        EnchantBook here = book("smp");
        EnchantBook there = book("lobby");
        here.unlock("g1", TELEKINESIS);
        servers.settle();
        assertThat(there.unlocked("g1", TELEKINESIS)).isTrue();

        EnchantBook restarted = new EnchantBook(servers.database());
        restarted.load();
        restarted.connect(NetworkBus.NONE);
        assertThat(restarted.unlocked("g1", TELEKINESIS)).isTrue();
    }

    @Test
    @DisplayName("unlocks recorded while shards still existed stay unlocked; half-paid ones are not unlocks")
    void oldRows() {
        servers.database().write(connection -> {
            for (Object[] row : new Object[][]{{"g1", TELEKINESIS, 100, 1}, {"g1", "rainsenchants:flight", 60, 0}}) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO guild_shard (guild, enchantment, paid, unlocked) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, (String) row[0]);
                    insert.setString(2, (String) row[1]);
                    insert.setInt(3, (Integer) row[2]);
                    insert.setInt(4, (Integer) row[3]);
                    insert.executeUpdate();
                }
            }
        });
        EnchantBook book = book("smp");
        assertThat(book.unlocked("g1", TELEKINESIS)).isTrue();
        assertThat(book.unlocked("g1", "rainsenchants:flight")).isFalse();
        assertThat(book.unlock("g1", "rainsenchants:flight")).as("a half-paid row is unlocked for money").contains(true);
        assertThat(book.unlock("g1", TELEKINESIS)).contains(false);
    }

    @Test
    @DisplayName("a disbanded guild's unlocks go with it")
    void forget() {
        EnchantBook book = book("smp");
        book.unlock("g1", TELEKINESIS);
        book.forget("g1");
        assertThat(book.unlocked("g1", TELEKINESIS)).isFalse();
        assertThat(book("lobby").unlocked("g1", TELEKINESIS)).isFalse();
    }
}
