package de.raindancer.modules.guilds.screen;

import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.prompt.AnvilInput;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.prompt.Parsers;
import de.raindancer.modules.guilds.GuildsServices;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Typing an amount of money into an anvil — "2500", "12.50", "1.5k" — read by Core's {@code Parsers.money} and checked
 * against a most while typing, so the player sees "more than you may take" before clicking, not after.
 */
final class AmountPrompt {

    private AmountPrompt() {
    }

    /** @param most the most that may be typed, in minor units; negative for no most */
    static void ask(GuildsServices services, Player viewer, String title, LongSupplier most, String tooMuch,
                    Consumer<Money> answer, Runnable cancelled) {
        long limit = most.getAsLong();
        Parsers.Parser<Money> amount = Parsers.money(Fees.currency(), false, Money.ZERO);
        AnvilInput.open(viewer, title, "", limit < 0 ? amount
                        : amount.andCheck(typed -> typed.minor() <= limit, tooMuch + " (" + services.bank().format(limit) + ")"),
                answer, cancelled);
    }
}
