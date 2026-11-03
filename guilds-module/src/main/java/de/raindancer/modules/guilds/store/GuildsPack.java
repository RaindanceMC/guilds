package de.raindancer.modules.guilds.store;

import de.raindancer.core.content.pack.JarPack;
import de.raindancer.core.content.pack.ResourcePacks;
import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.core.ui.tree.TreeLook;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * The guild looks — the skill tree's frames, lines and branch paintings — shipped in the jar and handed to Core's
 * resource pack. {@code files.txt} lists them; {@code art/tree/skilltree_art.py} writes both.
 */
public final class GuildsPack {

    public static final String NAMESPACE = "rainsguilds";
    /** The skill tree's frames and lines: heraldic shields and gilded chain, painted in art/tree. */
    public static final TreeLook TREE_LOOK = new TreeLook(Key.key(NAMESPACE, "tree"));
    /** The branches' paintings, in this order from U+E000 in the font rainsguilds:tree_scene. */
    static final List<String> SCENES = List.of("hall", "craft", "lore", "road", "banner");
    private static final TextColor TREE_INK = TextColor.color(0xF3E6C8);
    static final String ROOT = "/de/raindancer/modules/guilds/pack/";
    private static final String OWNER = "Guilds";
    private static final String NAME = "looks";

    private GuildsPack() {
    }

    public static List<String> files() {
        try (InputStream in = GuildsPack.class.getResourceAsStream(ROOT + "files.txt")) {
            if (in == null) {
                return List.of();
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    /** @throws IOException when the files could not be written out — guilds work on, the tree in Core's looks */
    public static void offer(Plugin plugin, ResourcePacks packs) throws IOException {
        JarPack.offer(plugin, packs, GuildsPack.class, ROOT, files(), OWNER, NAME, "The guild skill tree");
    }

    /** Takes the looks back out of Core's pack, when the module stops. */
    public static void withdraw(ResourcePacks packs) {
        packs.withdraw(OWNER.toLowerCase(Locale.ROOT) + ":" + NAME);
    }

    /** The painting behind a skill tree branch; {@code null} — the house's — for a branch without one. */
    public static Backdrop treeBackdrop(String branch) {
        int index = SCENES.indexOf(branch);
        if (index < 0) {
            return null;
        }
        return new Backdrop(Key.key(NAMESPACE, "tree_scene"), "" + (char) (0xE000 + index) + "", TREE_INK);
    }
}
