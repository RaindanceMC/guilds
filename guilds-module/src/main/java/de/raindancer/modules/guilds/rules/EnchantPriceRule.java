package de.raindancer.modules.guilds.rules;

import de.raindancer.core.social.economy.Currency;
import de.raindancer.core.social.economy.Money;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** What a guild pays from its bank to unlock one of Rain's enchantments: the base price, or the one a line names. */
public final class EnchantPriceRule implements IGuildsRule {

    static final Money BUILT_IN = Money.of(3000);

    private final Money base;
    private final Map<String, Money> named = new HashMap<>();

    /**
     * @param base   what an enchantment costs, as an owner wrote it; unreadable or negative gives the built-in price
     * @param prices {@code "<enchantment> <price>"} lines; an unreadable one is skipped, not fatal
     */
    public EnchantPriceRule(Currency currency, String base, List<String> prices) {
        this.base = amount(currency, base).orElse(BUILT_IN);
        for (String line : prices == null ? List.<String>of() : prices) {
            String[] words = line == null ? new String[0] : line.strip().split("\\s+");
            if (words.length == 2) {
                amount(currency, words[1]).ifPresent(price -> named.put(bare(words[0]), price));
            }
        }
    }

    private static Optional<Money> amount(Currency currency, String written) {
        return written == null ? Optional.empty() : currency.parse(written.strip()).filter(money -> money.minor() >= 0);
    }

    private static String bare(String enchantment) {
        return enchantment.substring(enchantment.indexOf(':') + 1).toLowerCase(Locale.ROOT);
    }

    /** @param enchantment its key, {@code rainsenchants:flight} */
    public Money price(String enchantment) {
        return named.getOrDefault(bare(enchantment), base);
    }

    @Override
    public String describe() {
        return "what a guild pays from its bank to unlock an enchantment";
    }
}
