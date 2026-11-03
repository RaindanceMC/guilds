package de.raindancer.modules.guilds.model;

import java.util.Locale;
import java.util.Optional;

/**
 * What a node of the guild skill tree does, per rank. Ranks of the same effect add up, to its {@link #cap()}.
 * A potion's value is its level (1 is Haste I); a "rally" potion only works near a guildmate.
 */
public enum Perk {

    VAULT_ROWS("The guild vault holds %s row(s) of nine stacks", 6),
    MEMBERS("Room for %s more members", 0),
    ALLIES("%s more ally slot(s)", 0),
    CONTRACTS("%s more contract(s) a week, from the next week", 6),
    CONTRACT_REWARD("Contracts pay %s%% more experience", 0),
    GUILD_XP("%s%% more guild experience", 0),
    VANILLA_XP("Members get %s%% more experience from orbs", 0),
    HOME_TRAVEL("The guild home's warm-up and wait are %s%% shorter", 100),
    TOGETHER("%s%% more guild experience near a guildmate", 0),

    XP_PLAY(XpSource.PLAY, "playing"),
    XP_MONSTER(XpSource.MONSTER, "monsters"),
    XP_BOSS(XpSource.BOSS, "bosses"),
    XP_ADVANCEMENT(XpSource.ADVANCEMENT, "advancements"),
    XP_MINE(XpSource.MINE, "mining"),
    XP_FISH(XpSource.FISH, "fishing"),
    XP_BREED(XpSource.BREED, "breeding"),
    XP_TRADE(XpSource.TRADE, "trading"),
    XP_HARVEST(XpSource.HARVEST, "harvesting"),
    XP_ENCHANT(XpSource.ENCHANT, "enchanting"),
    XP_RAID(XpSource.RAID, "raids"),
    XP_RIVAL(XpSource.RIVAL_KILL, "defeated rivals"),

    HASTE("haste", "Haste", 3, false),
    LUCK("luck", "Luck", 3, false),
    SPEED("speed", "Speed", 2, false),
    JUMP("jump_boost", "Jump Boost", 2, false),
    WATER_BREATHING("water_breathing", "Water Breathing", 1, false),
    DOLPHIN("dolphins_grace", "Dolphin's Grace", 1, false),
    NIGHT_VISION("night_vision", "Night Vision", 1, false),
    FIRE_RESISTANCE("fire_resistance", "Fire Resistance", 1, false),
    HERO("hero_of_the_village", "Hero of the Village", 3, false),
    CONDUIT("conduit_power", "Conduit Power", 1, false),
    RALLY_REGENERATION("regeneration", "Regeneration", 2, true),
    RALLY_STRENGTH("strength", "Strength", 1, true),
    RALLY_RESISTANCE("resistance", "Resistance", 1, true),
    RALLY_ABSORPTION("absorption", "Absorption", 1, true),
    RALLY_SPEED("speed", "Speed", 2, true),

    KEEP_LEVELS("%s%% chance to keep your levels when you die", 100),
    HUNGER_SAVE("%s%% chance hunger does not go down", 75),
    FALL_DAMAGE("%s%% less fall damage", 90);

    private static final String[] NUMERALS = {"", "I", "II", "III", "IV", "V"};

    private final String text;
    private final int cap;
    private final XpSource source;
    private final String potion;
    private final boolean rally;

    Perk(String text, int cap) {
        this(text, cap, null, null, false);
    }

    Perk(XpSource source, String from) {
        this("%s%% more guild experience from " + from, 0, source, null, false);
    }

    Perk(String potion, String name, int cap, boolean rally) {
        this(name + "%s", cap, null, potion, rally);
    }

    Perk(String text, int cap, XpSource source, String potion, boolean rally) {
        this.text = text;
        this.cap = cap;
        this.source = source;
        this.potion = potion;
        this.rally = rally;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The most all ranks together may add up to; 0 for no cap. */
    public int cap() {
        return cap;
    }

    /** The source this one adds guild experience for, or {@code null}. */
    public XpSource source() {
        return source;
    }

    /** The potion's key in the game's registry ({@code haste}), or {@code null} for no potion. */
    public String potion() {
        return potion;
    }

    /** A potion that only works with a guildmate near. */
    public boolean rally() {
        return rally;
    }

    /** "Room for 2 more members", "Members have Haste II", "Strength I near a guildmate". */
    public String describe(double value) {
        long whole = Math.round(value);
        String number = value == Math.rint(value) ? String.valueOf(whole) : String.valueOf(value);
        if (potion != null) {
            String level = cap > 1 || rally ? " " + (whole >= 0 && whole < NUMERALS.length ? NUMERALS[(int) whole] : number) : "";
            String name = String.format(Locale.ROOT, text, level);
            return rally ? name + " near a guildmate" : "Members have " + name;
        }
        return String.format(Locale.ROOT, text, number);
    }

    public static Optional<Perk> parse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(id.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public static boolean known(String id) {
        return parse(id).isPresent();
    }
}
