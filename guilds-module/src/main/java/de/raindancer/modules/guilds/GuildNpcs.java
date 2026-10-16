package de.raindancer.modules.guilds;

import de.raindancer.core.social.desk.ServiceDesk;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildPermission;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which attendants each guild placed itself, one of each kind: the leader places them, and every rank with
 * the rank permission ("Place attendants" — officers have it unless their leader takes it away). Bookkeeping only — {@link GuildNpcPlacing} puts them in the world.
 */
public final class GuildNpcs {

    public record Placed(UUID entity, String world, double x, double y, double z) {
    }

    private final Map<String, Map<ServiceDesk, Placed>> byGuild = new ConcurrentHashMap<>();

    public static boolean mayPlace(Guild guild, UUID player) {
        return guild.rankOf(player).isPresent() && guild.may(player, GuildPermission.PLACE_NPCS);
    }

    public Optional<Placed> placed(String guild, ServiceDesk desk) {
        return Optional.ofNullable(byGuild.getOrDefault(guild, Map.of()).get(desk));
    }

    public void record(String guild, ServiceDesk desk, Placed placed) {
        byGuild.computeIfAbsent(guild, id -> java.util.Collections.synchronizedMap(new EnumMap<>(ServiceDesk.class)))
                .put(desk, placed);
    }

    public Optional<Placed> forget(String guild, ServiceDesk desk) {
        Map<ServiceDesk, Placed> own = byGuild.get(guild);
        return own == null ? Optional.empty() : Optional.ofNullable(own.remove(desk));
    }

    public List<Placed> forgetGuild(String guild) {
        Map<ServiceDesk, Placed> own = byGuild.remove(guild);
        return own == null ? List.of() : List.copyOf(own.values());
    }

    /** Whether this attendant is the one on record for the guild — any other is left over and goes. */
    public boolean isCurrent(String guild, ServiceDesk desk, UUID entity) {
        return placed(guild, desk).map(Placed::entity).filter(entity::equals).isPresent();
    }

    /** One line per attendant: {@code guild desk entity world x y z}. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        byGuild.forEach((guild, desks) -> {
            synchronized (desks) {
                desks.forEach((desk, at) -> lines.add(String.join(" ", guild, desk.name(), at.entity().toString(),
                        at.world(), String.valueOf(at.x()), String.valueOf(at.y()), String.valueOf(at.z()))));
            }
        });
        return lines;
    }

    public void read(List<String> lines) {
        byGuild.clear();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 7) {
                continue;
            }
            try {
                ServiceDesk.read(parts[1].toLowerCase(Locale.ROOT)).ifPresent(desk -> record(parts[0], desk,
                        new Placed(UUID.fromString(parts[2]), parts[3], Double.parseDouble(parts[4]),
                                Double.parseDouble(parts[5]), Double.parseDouble(parts[6]))));
            } catch (IllegalArgumentException broken) {
                // a line somebody broke by hand: that attendant is forgotten, the others stay
            }
        }
    }
}
