package de.raindancer.modules.guilds.service;

import de.raindancer.core.content.enchant.CustomEnchantments;
import de.raindancer.core.content.enchant.EnchantUnlocks;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.guilds.GuildsSettings;
import de.raindancer.modules.guilds.model.Guild;
import de.raindancer.modules.guilds.model.GuildEvent;
import de.raindancer.modules.guilds.model.GuildOutcome;
import de.raindancer.modules.guilds.rules.EnchantPriceRule;
import de.raindancer.modules.guilds.store.EnchantBook;
import de.raindancer.modules.guilds.store.GuildBook;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A guild unlocking Rain's enchantments: it pays the price from its bank, then every member may buy the
 * enchantment's books — the guilds answer Core's {@link EnchantUnlocks} for it.
 */
public final class EnchantService implements EnchantUnlocks.Source {

    private static final String NAMESPACE = "rainsenchants";

    private final GuildBook guilds;
    private final EnchantBook unlocks;
    private final BankService bank;
    private final GuildNotifier notifier;
    private final Supplier<GuildsSettings> settings;

    public EnchantService(GuildBook guilds, EnchantBook unlocks, BankService bank, GuildNotifier notifier,
                          Supplier<GuildsSettings> settings) {
        this.guilds = guilds;
        this.unlocks = unlocks;
        this.bank = bank;
        this.notifier = notifier;
        this.settings = settings;
    }

    @Override
    public boolean unlocked(UUID player, String enchantment) {
        return guilds.guildOf(player).map(guild -> unlocks.unlocked(guild.id().value(), enchantment)).orElse(false);
    }

    public boolean unlocked(Guild guild, String enchantment) {
        return unlocks.unlocked(guild.id().value(), enchantment);
    }

    /** Rain's enchantments the server has switched on, in name order. */
    public List<String> enchantments() {
        List<String> keys = new ArrayList<>();
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).forEach(enchantment -> {
            if (enchantment.getKey().getNamespace().equals(NAMESPACE)
                    && !CustomEnchantments.isSwitchedOff(enchantment.getKey().asString())) {
                keys.add(enchantment.getKey().asString());
            }
        });
        keys.sort(Comparator.comparing(EnchantService::name));
        return keys;
    }

    public static String name(String enchantment) {
        return CustomEnchantments.find(enchantment)
                .map(found -> found.description())
                .map(description -> PlainTextComponentSerializer.plainText().serialize(description))
                .orElse(enchantment);
    }

    /** What the guild pays from its bank to unlock this one. */
    public Money price(String enchantment) {
        GuildsSettings live = settings.get();
        return new EnchantPriceRule(Fees.currency(), live.enchantPrice(), live.enchantPrices()).price(enchantment);
    }

    /**
     * The actor's guild pays for the enchantment out of its bank and has it from then on. Reads and writes the
     * database: off the server's threads. The bank is debited first and given back should the unlock not be
     * recorded — or another server has recorded it in the meantime — so a guild never pays for nothing.
     */
    public GuildOutcome unlock(UUID actor, String enchantment) {
        if (!enchantment.startsWith(NAMESPACE + ":")) {
            return GuildOutcome.NOT_ALLOWED;
        }
        Optional<Guild> found = guilds.guildOf(actor);
        if (found.isEmpty()) {
            return GuildOutcome.NO_GUILD;
        }
        Guild guild = found.get();
        if (unlocks.unlocked(guild.id().value(), enchantment)) {
            return GuildOutcome.ALREADY_UNLOCKED;
        }
        String name = name(enchantment);
        Money price = price(enchantment);
        GuildOutcome paid = bank.spend(guild, actor, price, "Unlocked " + name);
        if (!paid.isSuccess()) {
            return paid;
        }
        Optional<Boolean> done = unlocks.unlock(guild.id().value(), enchantment);
        if (done.isEmpty() || !done.get()) {
            bank.refund(guild, price);
            return done.isEmpty() ? GuildOutcome.NOT_SAVED : GuildOutcome.ALREADY_UNLOCKED;
        }
        guilds.log(GuildEvent.of(guild.id().value(), System.currentTimeMillis(), GuildEvent.Kind.ENCHANTMENT, actor,
                null, 0, name));
        notifier.members(guild, "guilds.enchant-unlocked", "player", notifier.name(actor), "name", name);
        return GuildOutcome.SUCCESS;
    }
}
