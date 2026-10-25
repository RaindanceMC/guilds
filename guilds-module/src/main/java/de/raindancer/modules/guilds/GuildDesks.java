package de.raindancer.modules.guilds;

import de.raindancer.core.social.desk.DeskOffer;
import de.raindancer.core.social.desk.ServiceDesk;
import de.raindancer.core.social.desk.ServiceDesks;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildOutcome;
import de.raindancer.modules.guilds.screen.BankMenu;
import de.raindancer.modules.guilds.screen.GuildEnchantsMenu;
import de.raindancer.modules.guilds.screen.GuildVaultMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The guild's attendants: a guild banker (the guild bank), a vault keeper (the guild vault) and a guild enchanter
 * (the enchantments the guild unlocks with shards). Each is a RainsCore desk with this one offer, so talking to them
 * opens the screen straight away.
 */
public final class GuildDesks {

    public static final List<ServiceDesk> DESKS = List.of(ServiceDesk.GUILD_BANK, ServiceDesk.GUILD_VAULT,
            ServiceDesk.GUILD_ENCHANTS);

    private final Supplier<GuildsServices> services;
    private final List<Offered> offers;

    private record Offered(ServiceDesk desk, DeskOffer offer) {
    }

    public GuildDesks(Supplier<GuildsServices> services) {
        this.services = services;
        this.offers = List.of(
                new Offered(ServiceDesk.GUILD_BANK, new Offer(Material.GOLD_BLOCK, "<white>The guild bank",
                        "<gray>Pay in or take out.", this::bank)),
                new Offered(ServiceDesk.GUILD_VAULT, new Offer(Material.ENDER_CHEST, "<white>The guild vault",
                        "<gray>The chest every member shares.", this::vault)),
                new Offered(ServiceDesk.GUILD_ENCHANTS, new Offer(Material.ENCHANTED_BOOK,
                        "<white>The guild's enchantments", "<gray>Pay in shards, unlock books for everybody.",
                        this::enchants)));
    }

    /** The desk staff mean by {@code bank}, {@code vault} or {@code enchants}. */
    public static Optional<ServiceDesk> named(String word) {
        return switch (word == null ? "" : word.toLowerCase(Locale.ROOT)) {
            case "bank", "banker" -> Optional.of(ServiceDesk.GUILD_BANK);
            case "vault", "keeper" -> Optional.of(ServiceDesk.GUILD_VAULT);
            case "enchants", "enchanter", "enchantments" -> Optional.of(ServiceDesk.GUILD_ENCHANTS);
            default -> Optional.empty();
        };
    }

    public static void skins(GuildNpcSettings settings) {
        ServiceDesks.skins(ServiceDesk.GUILD_BANK, settings.bankSkins());
        ServiceDesks.skins(ServiceDesk.GUILD_VAULT, settings.vaultSkins());
        ServiceDesks.skins(ServiceDesk.GUILD_ENCHANTS, settings.enchanterSkins());
    }

    public void offer() {
        offers.forEach(each -> ServiceDesks.offer(each.desk(), each.offer()));
    }

    public void withdraw() {
        offers.forEach(each -> ServiceDesks.withdraw(each.offer()));
    }

    private void bank(GuildsServices live, Player player, Guild guild) {
        if (!live.bank().isOpen()) {
            live.messages().send(player, "guilds.npc.no-bank");
            return;
        }
        new BankMenu(live, player, null).open();
    }

    private void vault(GuildsServices live, Player player, Guild guild) {
        if (!live.perks().hasVault(guild)) {
            live.messages().send(player, "guilds.vault.none");
            return;
        }
        GuildVaultMenu.show(live, player, null);
    }

    private void enchants(GuildsServices live, Player player, Guild guild) {
        new GuildEnchantsMenu(live, player, null).open();
    }

    private interface Opens {
        void open(GuildsServices live, Player player, Guild guild);
    }

    private final class Offer implements DeskOffer {
        private final Material icon;
        private final String title;
        private final String line;
        private final Opens opens;

        Offer(Material icon, String title, String line, Opens opens) {
            this.icon = icon;
            this.title = title;
            this.line = line;
            this.opens = opens;
        }

        @Override
        public boolean available() {
            return services.get() != null;
        }

        @Override
        public ItemStack icon(Player viewer) {
            return Icons.of(icon, title, line, "<yellow>Click<gray> to open");
        }

        @Override
        public void use(Player viewer, Runnable backToDesk) {
            GuildsServices live = services.get();
            if (live == null) {
                return;
            }
            Optional<Guild> guild = live.guilds().guildOf(viewer.getUniqueId());
            if (guild.isEmpty()) {
                live.messages().send(viewer, GuildOutcome.NO_GUILD.messageKey());
                return;
            }
            opens.open(live, viewer, guild.get());
        }
    }
}
