package de.raindancer.modules.guilds.listener;

import de.raindancer.modules.guilds.GuildsServices;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * What the skill tree changes in a member's own play: experience orbs, levels kept through death, hunger and falls.
 * Late, so other plugins' changes come first; nothing here reads the database — the standing is known ahead.
 */
public final class PerkListener implements IGuildsListener {

    private final GuildsServices services;

    public PerkListener(GuildsServices services) {
        this.services = services;
    }

    @Override
    public void forget(java.util.UUID player) {
        // Nothing is remembered per player here.
    }

    private static boolean chance(int percent) {
        return percent > 0 && (percent >= 100 || ThreadLocalRandom.current().nextInt(100) < percent);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExperience(PlayerExpChangeEvent event) {
        event.setAmount(services.perks().vanillaXp(event.getPlayer().getUniqueId(), event.getAmount()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepLevel() || !chance(services.perks().keepLevelsPercent(event.getPlayer().getUniqueId()))) {
            return;
        }
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        services.messages().send(event.getPlayer(), "guilds.perk.levels-kept");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }
        if (chance(services.perks().hungerSavePercent(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        int less = Math.min(100, services.perks().fallDamagePercent(player.getUniqueId()));
        if (less > 0) {
            event.setDamage(event.getDamage() * (100 - less) / 100.0);
        }
    }
}
