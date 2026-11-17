package de.raindancer.modules.guilds.rules;

import de.raindancer.core.social.economy.Currency;
import de.raindancer.core.social.economy.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnchantPriceRuleTest {

    private final Currency currency = Currency.DEFAULT;

    private Money coins(long whole) {
        return currency.parse(String.valueOf(whole)).orElseThrow();
    }

    @Test
    @DisplayName("every enchantment costs the base price unless a line names it")
    void base() {
        EnchantPriceRule rule = new EnchantPriceRule(currency, "3000", List.of("flight 6000", "rainsenchants:launch 7000"));
        assertThat(rule.price("rainsenchants:telekinesis")).isEqualTo(coins(3000));
        assertThat(rule.price("rainsenchants:flight")).isEqualTo(coins(6000));
        assertThat(rule.price("rainsenchants:launch")).as("the namespace may be written").isEqualTo(coins(7000));
    }

    @Test
    @DisplayName("an unreadable line is skipped, an unreadable base price falls back to the built-in one")
    void mangled() {
        EnchantPriceRule rule = new EnchantPriceRule(currency, "plenty", List.of("flight", "flight lots", "boost 10"));
        assertThat(rule.price("rainsenchants:flight")).isEqualTo(EnchantPriceRule.BUILT_IN);
        assertThat(rule.price("rainsenchants:boost")).isEqualTo(coins(10));
    }

    @Test
    @DisplayName("zero is a price: the guild unlocks it for nothing")
    void free() {
        assertThat(new EnchantPriceRule(currency, "0", List.of()).price("rainsenchants:flight")).isEqualTo(Money.ZERO);
    }
}
