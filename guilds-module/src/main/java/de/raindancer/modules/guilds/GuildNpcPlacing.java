package de.raindancer.modules.guilds;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.desk.ServiceDesk;
import de.raindancer.core.social.desk.ServiceDesks;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.protection.LandAction;
import de.raindancer.modules.guilds.model.Guild;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Attendants a guild places itself ({@code /guild npc}): put in the world where the leader or an officer stands —
 * where they may build — one of each kind, kept on record in {@code npcs.yml} (a row in Core's database, so only
 * ever read and written off the server thread). One that is no longer on record (taken away while its chunk was not
 * loaded, or the guild disbanded) goes the next time its chunk loads.
 */
public final class GuildNpcPlacing implements Listener {

    public enum Result { PLACED, NOT_ALLOWED, NOT_HERE, ALREADY, NOT_READY }

    private final Plugin plugin;
    private final YamlStore store;
    private final NamespacedKey key;
    private final GuildNpcs npcs = new GuildNpcs();
    private volatile boolean loaded;

    public GuildNpcPlacing(Plugin plugin, YamlStore store) {
        this.plugin = plugin;
        this.store = store;
        this.key = new NamespacedKey(plugin, "npc-of");
    }

    public void load() {
        Scheduling.async(plugin, () -> {
            npcs.read(store.read().getStringList("npcs"));
            loaded = true;
        });
    }

    private void save() {
        List<String> lines = npcs.lines();
        Scheduling.async(plugin, () -> store.write(yaml -> yaml.set("npcs", lines)));
    }

    /** On the player's own thread. */
    public Result place(Player player, Guild guild, ServiceDesk desk, Land land) {
        if (!loaded) {
            return Result.NOT_READY;
        }
        if (!GuildNpcs.mayPlace(guild, player.getUniqueId())) {
            return Result.NOT_ALLOWED;
        }
        Location here = player.getLocation();
        if (!land.can(player, here, LandAction.BUILD)) {
            return Result.NOT_HERE;
        }
        String id = guild.id().value();
        if (npcs.placed(id, desk).isPresent()) {
            return Result.ALREADY;
        }
        Mannequin attendant = ServiceDesks.place(player, desk);
        attendant.getPersistentDataContainer().set(key, PersistentDataType.STRING, id + " " + desk.name());
        npcs.record(id, desk, new GuildNpcs.Placed(attendant.getUniqueId(), here.getWorld().getName(),
                attendant.getX(), attendant.getY(), attendant.getZ()));
        save();
        return Result.PLACED;
    }

    /** Whether the guild had one of these to take away. */
    public boolean remove(Guild guild, ServiceDesk desk) {
        return npcs.forget(guild.id().value(), desk).map(placed -> {
            gone(placed);
            save();
            return true;
        }).orElse(false);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void disbanded(String guild) {
        List<GuildNpcs.Placed> those = npcs.forgetGuild(guild);
        if (!those.isEmpty()) {
            those.forEach(this::gone);
            save();
        }
    }

    private void gone(GuildNpcs.Placed placed) {
        World world = Bukkit.getWorld(placed.world());
        if (world == null) {
            return;
        }
        Location at = new Location(world, placed.x(), placed.y(), placed.z());
        Scheduling.region(plugin, at, () -> {
            Entity entity = world.getEntity(placed.entity());
            if (entity != null) {
                entity.remove();
            }
        });
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        if (!loaded) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof Mannequin)) {
                continue;
            }
            String of = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (of == null) {
                continue;
            }
            String[] parts = of.split(" ");
            boolean current = parts.length == 2 && ServiceDesk.read(parts[1])
                    .filter(desk -> npcs.isCurrent(parts[0], desk, entity.getUniqueId())).isPresent();
            if (!current) {
                entity.remove();
            }
        }
    }
}
