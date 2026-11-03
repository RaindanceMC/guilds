package de.raindancer.modules.guilds.rules;

import de.raindancer.core.world.activity.Ores;
import de.raindancer.modules.guilds.GuildProgressSettings;
import de.raindancer.modules.guilds.GuildsSettings;
import de.raindancer.modules.guilds.model.Activity;

/** What a member's activity is worth to their guild before caps and bonuses. */
public final class ActivityRule implements IGuildsRule {

    @Override
    public String describe() {
        return "an activity is worth its setting times how many, ores times how rare they are";
    }

    /**
     * What an activity is worth, before the member's cap and any bonus. A deposit's amount is in whole thousands
     * of the currency — the bank works that out, since only it knows the currency.
     */
    public long worth(Activity activity, GuildsSettings base, GuildProgressSettings more) {
        long each = switch (activity.source()) {
            case PLAY -> base.xpPerMinute();
            case MONSTER -> base.xpPerMonster();
            case BOSS -> base.xpPerBoss();
            case ADVANCEMENT -> base.xpPerAdvancement();
            case MINE -> (long) more.perOre() * Ores.weight(activity.what());
            case FISH -> more.perFish();
            case BREED -> more.perBreed();
            case TRADE -> more.perTrade();
            case HARVEST -> more.perHarvest();
            case ENCHANT -> more.perEnchantLevel();
            case RAID -> more.perRaid();
            case RIVAL_KILL -> more.perRivalKill();
            case DEPOSIT -> more.perThousandDeposited();
            case CONTRACT, STAFF -> 1;
        };
        long amount = activity.amount();
        return each <= 0 || amount <= 0 ? 0 : (each > Long.MAX_VALUE / amount ? Long.MAX_VALUE : each * amount);
    }
}
