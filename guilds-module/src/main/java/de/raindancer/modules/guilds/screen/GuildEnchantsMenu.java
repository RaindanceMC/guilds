package de.raindancer.modules.guilds.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.guilds.GuildsServices;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.service.EnchantService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** Rain's enchantments and which the guild has: click a locked one to unlock it for the price, out of the guild bank. */
public final class GuildEnchantsMenu extends PaginatedMenu<String> implements IGuildsScreen {

    private final GuildsServices services;

    public GuildEnchantsMenu(GuildsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    private Optional<Guild> guild() {
        return services.guilds().guildOf(viewer.getUniqueId());
    }

    @Override
    protected Component title() {
        return Component.text("Enchantments", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Enchantments";
    }

    @Override
    protected List<String> entries() {
        return guild().isPresent() ? services.enchants().enchantments() : List.of();
    }

    @Override
    protected ItemStack icon(String enchantment) {
        String name = GuildMenu.escaped(EnchantService.name(enchantment));
        Optional<Guild> guild = guild();
        if (guild.isPresent() && services.enchants().unlocked(guild.get(), enchantment)) {
            return Icons.of(Material.ENCHANTED_BOOK, "<gold>" + name, "<green>Unlocked for your guild.",
                    "", "<gray>Buy its books at the shop.");
        }
        String price = services.bank().format(services.enchants().price(enchantment).minor());
        return Icons.of(Material.BOOK, "<white>" + name,
                "<gray>Costs <white>" + price + "<gray> from the guild bank.",
                "<gray>The bank holds <white>" + services.bank().format(guild.map(Guild::bank).orElse(0L)) + "<gray>.",
                "", "<yellow>Click<gray> to unlock it for everybody.");
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>Nothing to unlock", "<gray>Rain's enchantments are not on this server.");
    }

    @Override
    protected void onClick(String enchantment, InventoryClickEvent event) {
        if (guild().map(guild -> services.enchants().unlocked(guild, enchantment)).orElse(true)) {
            return;
        }
        confirm(enchantment).open();
    }

    private ConfirmScreen confirm(String enchantment) {
        String price = services.bank().format(services.enchants().price(enchantment).minor());
        return new ConfirmScreen(services, viewer, this, "<white>Unlock " + GuildMenu.escaped(EnchantService.name(enchantment)) + "?",
                List.of("<gray>The guild bank pays <white>" + price + "<gray>.", "<gray>Every member can then buy",
                        "<gray>its books at the shop."),
                () -> unlock(enchantment));
    }

    private void unlock(String enchantment) {
        IGuildsScreen.off(services, viewer, () -> services.enchants().unlock(viewer.getUniqueId(), enchantment), outcome -> {
            if (!outcome.isSuccess()) {
                services.messages().send(viewer, outcome.messageKey());
            }
            open();
        });
    }

    @Override
    public String describe() {
        return "Rain's enchantments the guild unlocks from its bank";
    }
}
