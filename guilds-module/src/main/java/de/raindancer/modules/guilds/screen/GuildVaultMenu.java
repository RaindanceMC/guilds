package de.raindancer.modules.guilds.screen;

import de.raindancer.core.data.stash.ArmourPiece;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.guilds.GuildsServices;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildPermission;
import de.raindancer.modules.guilds.store.VaultStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The guild vault, shared by every member. Nothing in the window is a real item: the slots show copies, and every
 * click asks the vault to move something — the moderation vault's way, for the same reason: a copy that can be
 * picked up is a duplicated item. Anybody in the guild puts things in; taking out is a rank's permission.
 */
public final class GuildVaultMenu extends Menu implements IGuildsScreen {

    private static final int PER_PAGE = 36;
    private static final Set<ClickType> TAKING = Set.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);

    private final GuildsServices services;
    private int page;
    /** The vault as it was last read, off the server's threads: the page is drawn from this, never from the database. */
    private volatile Optional<VaultStore.Contents> held = Optional.empty();

    public GuildVaultMenu(GuildsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return Component.text("Guild vault", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Vault";
    }

    /** Opens the vault once it is read — off the server's threads. */
    public static void show(GuildsServices services, Player viewer, Menu parent) {
        GuildVaultMenu menu = new GuildVaultMenu(services, viewer, parent);
        menu.load(menu::open);
    }

    /** Reads the vault again, then {@code then} on the viewer's own thread. */
    private void load(Runnable then) {
        Optional<Guild> found = guild();
        if (found.isEmpty()) {
            then.run();
            return;
        }
        IGuildsScreen.off(services, viewer, () -> services.perks().vaultContents(found.get()), contents -> {
            held = contents;
            then.run();
        });
    }

    private List<ItemStack> items() {
        return held.map(VaultStore.Contents::items).orElse(List.of());
    }

    /** Asked again on every click: a member removed while the window is open must not keep using it. */
    private Optional<Guild> guild() {
        return services.guilds().guildOf(viewer.getUniqueId());
    }

    private int pages(Guild guild) {
        int stored = items().size();
        int size = Math.max(stored + 1, services.perks().vaultSlots(guild));
        return Math.max(1, MenuLayout.pageCount(size, PER_PAGE));
    }

    @Override
    protected void render() {
        Optional<Guild> found = guild();
        if (found.isEmpty()) {
            band(MenuLayout.WHO, 4, Icons.of(Material.BARRIER, "<red>You are in no guild now"));
            return;
        }
        Guild guild = found.get();
        List<ItemStack> items = items();
        page = MenuLayout.clampPage(page, pages(guild));
        int from = page * PER_PAGE;
        for (int slot = 0; slot < PER_PAGE && from + slot < items.size(); slot++) {
            int index = from + slot;
            ItemStack shown = items.get(index);
            set(slot, shown, event -> {
                if (TAKING.contains(event.getClick())) {
                    take(index, shown);
                }
            });
        }
    }

    private void take(int index, ItemStack shown) {
        Optional<Guild> found = guild();
        if (found.isEmpty()) {
            viewer.closeInventory();
            return;
        }
        Guild guild = found.get();
        if (!guild.may(viewer.getUniqueId(), GuildPermission.VAULT)) {
            services.messages().send(viewer, "guilds.vault.no-taking");
            return;
        }
        UUID me = viewer.getUniqueId();
        IGuildsScreen.off(services, viewer, () -> services.perks().take(guild, me, index, shown), taken -> hand(guild, taken));
    }

    /** Into their inventory; what does not fit goes back into the vault, never on the floor. */
    private void hand(Guild guild, ItemStack taken) {
        if (taken == null) {
            load(this::refresh);
            return;
        }
        Map<Integer, ItemStack> left = viewer.getInventory().addItem(taken);
        if (left.isEmpty()) {
            load(this::refresh);
            return;
        }
        services.messages().send(viewer, "guilds.vault.inventory-full");
        IGuildsScreen.off(services, viewer, () -> {
            left.values().forEach(back -> services.perks().putBack(guild, back));
            return Boolean.TRUE;
        }, done -> load(this::refresh));
    }

    /** What the vault did not take comes back to the player: the cursor if it is free, else the inventory, else the floor. */
    private void giveBack(ItemStack back) {
        if (viewer.getItemOnCursor().isEmpty()) {
            viewer.setItemOnCursor(back);
            return;
        }
        viewer.getInventory().addItem(back).values()
                .forEach(rest -> viewer.getWorld().dropItem(viewer.getLocation(), rest));
    }

    /** Takes {@code offered} out of the player's hands first — so it cannot be in two places — then puts it in the vault. */
    private void putIn(Guild guild, ItemStack offered) {
        IGuildsScreen.off(services, viewer, () -> services.perks().deposit(guild, offered), accepted -> {
            if (accepted < offered.getAmount()) {
                services.messages().send(viewer, "guilds.vault.full");
                giveBack(offered.asQuantity(offered.getAmount() - accepted));
            }
            load(this::refresh);
        });
    }

    @Override
    protected void decorate() {
        Optional<Guild> found = guild();
        if (found.isPresent()) {
            Guild guild = found.get();
            int column = 2;
            for (ArmourPiece piece : ArmourPiece.values()) {
                ItemStack shown = held.map(contents -> contents.armour().get(piece)).orElseGet(() -> Icons.of(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>" + piece.label() + " stand <dark_gray>— empty",
                        "<dark_gray>Armour put in goes onto its stand first."));
                toolbar(column++, shown, click -> {
                    // Asked at the click, not when the page was drawn: a rank can lose the right while it is open.
                    Optional<Guild> now = guild();
                    if (now.isEmpty() || !now.get().may(viewer.getUniqueId(), GuildPermission.VAULT)) {
                        services.messages().send(viewer, "guilds.vault.no-taking");
                        return;
                    }
                    Guild current = now.get();
                    UUID me = viewer.getUniqueId();
                    IGuildsScreen.off(services, viewer, () -> services.perks().takeArmour(current, me, piece),
                            taken -> hand(current, taken));
                });
            }
        }
        fillRow(MenuLayout.TOOLBAR_ROW, Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    protected void paintPagingChrome(int chromeRow) {
        Optional<Guild> found = guild();
        int pages = found.map(this::pages).orElse(1);
        if (page > 0) {
            set(chromeRow + MenuLayout.CHROME_PREVIOUS, Icons.previousPage(page, pages), event -> turnTo(page - 1));
        }
        if (page < pages - 1) {
            set(chromeRow + MenuLayout.CHROME_NEXT, Icons.nextPage(page + 2, pages), event -> turnTo(page + 1));
        }
        set(chromeRow + MenuLayout.CHROME_PAGE, Icons.pageCounter(page + 1, pages));
    }

    private void turnTo(int newPage) {
        page = newPage;
        refresh();
    }

    @Override
    protected List<String> helpLines() {
        return List.of("<gray>Shift-click anything in your inventory",
                "<gray>to put it in, or drop it onto a page.",
                "<gray>Click a stack to take it out — if your",
                "<gray>rank may. Armour goes onto its stand.",
                "<dark_gray>More rows with the Guild vault perk.");
    }

    /** A click with something on the cursor, anywhere on the page, puts it in. */
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        if (slot >= 0 && slot < PER_PAGE && !cursor.isEmpty()) {
            event.setCancelled(true);
            Optional<Guild> found = guild();
            if (found.isEmpty()) {
                viewer.closeInventory();
                return;
            }
            ItemStack offered = cursor.clone();
            viewer.setItemOnCursor(null);
            putIn(found.get(), offered);
            return;
        }
        super.handleClick(event);
    }

    @Override
    public boolean allowBottomInventoryInteraction() {
        return true;
    }

    /** Their own inventory: shift-click puts the stack in; a double click may not gather copies off the page. */
    @Override
    public void handleBottomClick(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (!event.isShiftClick()) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        Optional<Guild> found = guild();
        if (clicked == null || clicked.isEmpty() || found.isEmpty()) {
            return;
        }
        ItemStack offered = clicked.clone();
        event.setCurrentItem(null);
        putIn(found.get(), offered);
    }

    @Override
    public String describe() {
        return "the guild's shared vault";
    }
}
