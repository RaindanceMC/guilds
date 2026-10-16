package de.raindancer.modules.guilds;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

/** The skins of the guild's attendants. Its own record so the others keep their shape. */
@Settings(id = "guilds-npcs", topics = {
        @Topic(path = "guilds-npcs", title = "Guild attendants", icon = Material.ARMOR_STAND,
                description = "The guild banker, the vault keeper and the guild enchanter staff place with "
                        + "/guild admin npc."),
})
public record GuildNpcSettings(

        @In("guilds-npcs") @Title("Guild bankers' skins")
        @Describe("A player name, a MineSkin link (mineskin.org) or mineskin:<id>; several, comma-separated, are "
                + "shared out. Empty: a black suit. Applies to attendants placed afterwards.")
        @Key("bank-skins") String bankSkins,

        @In("guilds-npcs") @Title("Vault keepers' skins")
        @Describe("Like the guild bankers' skins.")
        @Key("vault-skins") String vaultSkins,

        @In("guilds-npcs") @Title("Guild enchanters' skins")
        @Describe("Like the guild bankers' skins.")
        @Key("enchanter-skins") String enchanterSkins) {

    public static final GuildNpcSettings DEFAULTS = new GuildNpcSettings("", "", "");
}
