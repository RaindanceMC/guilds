package de.raindancer.modules.guilds.listener;

import de.raindancer.modules.guilds.GuildsServices;
import de.raindancer.modules.guilds.model.Activity;
import de.raindancer.modules.guilds.model.XpSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Optional;

/**
 * What members do that earns their guild experience and counts towards its contracts. Core's
 * {@link de.raindancer.core.world.activity.Activities} decides what really happened (never an ore a player placed);
 * each becomes one {@link Activity}, and what it is worth and whether it counts is decided past here. Rivals defeated
 * are the guild's own.
 */
public final class ProgressListener implements IGuildsListener {

    private final GuildsServices services;

    public ProgressListener(GuildsServices services) {
        this.services = services;
    }

    /** What Core heard, as the guild counts it; empty for minutes played and for kinds the guild does not know. */
    public static Optional<Activity> toGuild(de.raindancer.core.world.activity.Activity activity) {
        XpSource source = switch (activity.kind()) {
            case MONSTER -> XpSource.MONSTER;
            case BOSS -> XpSource.BOSS;
            case ADVANCEMENT -> XpSource.ADVANCEMENT;
            case MINE -> XpSource.MINE;
            case FISH -> XpSource.FISH;
            case BREED -> XpSource.BREED;
            case TRADE -> XpSource.TRADE;
            case HARVEST -> XpSource.HARVEST;
            case ENCHANT -> XpSource.ENCHANT;
            case RAID -> XpSource.RAID;
            // PLAY: the guild counts its own minutes. Anything a newer Core tells earns nothing yet.
            default -> null;
        };
        return Optional.ofNullable(source).map(each -> new Activity(each, activity.what(), activity.amount()));
    }

    /** Handed to {@code Activities.listen}. */
    public void heard(Player player, de.raindancer.core.world.activity.Activity activity) {
        toGuild(activity).ifPresent(each -> services.xp().activity(player.getUniqueId(), each));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKilled(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !killer.equals(event.getEntity())) {
            services.xp().rivalDefeated(killer.getUniqueId(), event.getEntity().getUniqueId());
        }
    }
}
