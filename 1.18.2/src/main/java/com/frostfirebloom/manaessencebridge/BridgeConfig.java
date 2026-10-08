package com.frostfirebloom.manaessencebridge;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Настройки мода. Лежат в config/manaessencebridge-common.toml и правятся
 * без пересборки jar - чтобы баланс можно было крутить прямо в сборке.
 *
 * Класс называется BridgeConfig, а не ModConfig, чтобы не путаться с
 * net.minecraftforge.fml.config.ModConfig из самого Forge.
 */
public final class BridgeConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private BridgeConfig() {
    }

    public static final class Common {

        public final ForgeConfigSpec.IntValue manaPerInferium;
        public final ForgeConfigSpec.IntValue buyMarkupPercent;
        public final ForgeConfigSpec.IntValue maxTier;
        public final ForgeConfigSpec.BooleanValue enableInsanium;
        public final ForgeConfigSpec.BooleanValue buyHighestTier;
        public final ForgeConfigSpec.BooleanValue automationEnabled;
        public final ForgeConfigSpec.BooleanValue pullFromContainers;
        public final ForgeConfigSpec.IntValue pullIntervalTicks;
        public final ForgeConfigSpec.IntValue condenserIntervalTicks;
        public final ForgeConfigSpec.BooleanValue boostCapacity;
        public final ForgeConfigSpec.BooleanValue hideLockedInJei;
        public final ForgeConfigSpec.BooleanValue showHud;
        public final ForgeConfigSpec.BooleanValue poolGlow;
        public final ForgeConfigSpec.BooleanValue animateFloatingFlowers;
        public final ForgeConfigSpec.BooleanValue privatePools;
        public final ForgeConfigSpec.BooleanValue keepManaOnBreak;
        public final ForgeConfigSpec.IntValue expanderPercent;
        public final ForgeConfigSpec.IntValue expanderMaxCrystals;
        public final ForgeConfigSpec.IntValue manaFertilizerStages;
        public final ForgeConfigSpec.IntValue poolLinkRate;
        public final ForgeConfigSpec.IntValue mysticarnationRange;
        public final ForgeConfigSpec.IntValue reaperbloomRange;
        public final ForgeConfigSpec.IntValue wardeniaMoistenCost;
        public final ForgeConfigSpec.IntValue wardeniaPushCost;
        public final ForgeConfigSpec.IntValue wardeniaProjectileCost;
        public final ForgeConfigSpec.IntValue wardeniaExplosionCost;
        public final ForgeConfigSpec.IntValue essentidePercent;
        public final ForgeConfigSpec.IntValue brookbellManaPerItem;
        public final ForgeConfigSpec.IntValue brookbellMaxPerSecond;
        public final ForgeConfigSpec.IntValue boltbloomStrikeMana;
        public final ForgeConfigSpec.IntValue boltbloomSelfStrikeSeconds;
        public final ForgeConfigSpec.IntValue melodiaNoteMana;
        public final ForgeConfigSpec.IntValue melodiaMaxPerSecond;
        public final ForgeConfigSpec.IntValue bumblebloomMana;
        public final ForgeConfigSpec.IntValue bumblebloomCooldownSeconds;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Exchange rates").push("balance");

            manaPerInferium = builder
                    .comment(
                            "Mana given by one Inferium Essence. Each tier above is worth exactly 4x more.",
                            "The x4 step is fixed on purpose: Mystical Agriculture converts essence 4:1",
                            "both ways for free, so any better rate becomes an infinite mana loop.")
                    .defineInRange("manaPerInferium", 2000, 1, 1_000_000);

            buyMarkupPercent = builder
                    .comment("Buy-back price, as a percent of the sell price. 100 = lossless trade.")
                    .defineInRange("buyMarkupPercent", 125, 100, 1000);

            maxTier = builder
                    .comment("Highest usable tier. 1 = Inferium only, 5 = up to Supremium.")
                    .defineInRange("maxTier", 5, 1, 5);

            enableInsanium = builder
                    .comment("Tier 6, Insanium: only when Mystical Agradditions is installed and maxTier is 5.",
                            "The catalyst needs a defeated Gaia Guardian II.")
                    .define("enableInsanium", true);

            builder.pop();
            builder.comment("Gameplay").push("gameplay");

            buyHighestTier = builder
                    .comment(
                            "What Shift + right-click with an empty hand buys.",
                            "true = the highest tier the pool supports and you can afford.",
                            "false = always Inferium, so a misclick is cheap; the price is identical",
                            "either way, since crafting essence upward is free.")
                    .define("buyHighestTier", true);

            automationEnabled = builder
                    .comment(
                            "Let hoppers and pipes insert essence into an upgraded pool.",
                            "Insert only - nothing can be pulled back out.")
                    .define("automationEnabled", true);

            pullFromContainers = builder
                    .comment(
                            "Server-wide switch for auto-pull: an upgraded pool drawing essence out of",
                            "the containers touching it. Each pool still has to be switched on with an",
                            "Essence Siphon, and a redstone signal on the pool pauses it.")
                    .define("pullFromContainers", true);

            pullIntervalTicks = builder
                    .comment("How often an upgraded pool checks its neighbours, in ticks (20 = once a second).")
                    .defineInRange("pullIntervalTicks", 20, 1, 200);

            condenserIntervalTicks = builder
                    .comment("How often an Essence Condenser takes one essence worth of mana from its pool,",
                            "in ticks. 2 = ten essences a second at most, drawn evenly, not in one gulp.")
                    .defineInRange("condenserIntervalTicks", 2, 1, 200);

            boostCapacity = builder
                    .comment(
                            "Upgraded pools hold more mana: x2 per tier above the first, up to x16 at tier 5.",
                            "Turning this off returns pools to their normal size the next time a player",
                            "sees them; mana already above that size stays until it is spent.")
                    .define("boostCapacity", true);

            hideLockedInJei = builder
                    .comment(
                            "Hide catalysts in JEI until their Botania gate is passed",
                            "(Terrasteel / Alfheim portal / Gaia Guardian).")
                    .define("hideLockedInJei", true);

            showHud = builder
                    .comment("Show tier and exchange rate when looking at an upgraded pool.")
                    .define("showHud", true);

            poolGlow = builder
                    .comment("Faint tier-coloured sparks over upgraded pools, so their tier shows from afar.",
                            "Client-side only.")
                    .define("poolGlow", true);

            animateFloatingFlowers = builder
                    .comment("Floating flowers of this mod slowly spin and bob, like Botania's.",
                            "Client-side only; false draws them still.")
                    .define("animateFloatingFlowers", true);

            privatePools = builder
                    .comment("Only a pool's owner (the player who placed its first catalyst) can buy essence",
                            "from it, remove its tier, switch its Essence Siphon or tie a condenser to it.",
                            "Operators are not limited. For public servers.")
                    .define("privatePools", false);

            keepManaOnBreak = builder
                    .comment("A mined upgraded pool keeps its mana in the item and gets it back when placed.")
                    .define("keepManaOnBreak", true);

            expanderPercent = builder
                    .comment("Capacity an Expander Crystal adds to the upgraded pool it touches (directly or through",
                            "other crystals), as a percent of the pool's capacity at its tier.")
                    .defineInRange("expanderPercent", 25, 1, 100);

            expanderMaxCrystals = builder
                    .comment("How many Expander Crystals one pool counts at most.")
                    .defineInRange("expanderMaxCrystals", 8, 1, 64);

            manaFertilizerStages = builder
                    .comment("Growth stages a Mana Fertilizer gives every Mystical Agriculture crop in its 3x3 area.",
                            "Its mana price is in the recipe manaessencebridge:mana_infusion/mana_fertilizer (45000).",
                            "Raise both together, or growing crops gets cheaper than their essence is worth.")
                    .defineInRange("manaFertilizerStages", 2, 1, 7);

            poolLinkRate = builder
                    .comment("Pool network: mana a link between two pools moves per second, at most.",
                            "Linked pools even out how full they are. 0 turns the network off.")
                    .defineInRange("poolLinkRate", 50000, 0, 100000000);

            builder.pop();
            builder.comment("Flowers").push("flowers");

            mysticarnationRange = builder
                    .comment("Mysticarnation reach: blocks to each side (3 = a 7x7 square).")
                    .defineInRange("mysticarnationRange", 3, 1, 6);

            reaperbloomRange = builder
                    .comment("Reaperbloom reach: blocks to each side (5 = an 11x11 square).")
                    .defineInRange("reaperbloomRange", 5, 1, 8);

            wardeniaMoistenCost = builder
                    .comment("Mana the Wardenia spends to moisten one farmland block.")
                    .defineInRange("wardeniaMoistenCost", 1, 0, 1000);

            wardeniaPushCost = builder
                    .comment("Mana the Wardenia spends to push one hostile mob away.")
                    .defineInRange("wardeniaPushCost", 20, 0, 10000);

            wardeniaProjectileCost = builder
                    .comment("Mana the Wardenia spends to stop one projectile shot by a hostile mob.")
                    .defineInRange("wardeniaProjectileCost", 10, 0, 10000);

            wardeniaExplosionCost = builder
                    .comment("Mana the Wardenia spends to stop an explosion in its area,",
                            "or to shield its area from an explosion next to it.")
                    .defineInRange("wardeniaExplosionCost", 200, 0, 100000);

            essentidePercent = builder
                    .comment("Mana an Essentide makes from a tier-1 resource essence, as a percent of the Inferium price;",
                            "it doubles with each crop tier. Keep it low: at 10 even two tier-5 essences are worth less",
                            "than growing the crop with a Mysticarnation.")
                    .defineInRange("essentidePercent", 10, 1, 100);

            brookbellManaPerItem = builder
                    .comment("Mana a Brookbell makes per item drifting past.")
                    .defineInRange("brookbellManaPerItem", 5, 1, 1000);

            brookbellMaxPerSecond = builder
                    .comment("Brookbell cap, mana per second - stops dropper loops.")
                    .defineInRange("brookbellMaxPerSecond", 20, 1, 10000);

            boltbloomStrikeMana = builder
                    .comment("Mana a Boltbloom makes from one lightning strike within 8 blocks.")
                    .defineInRange("boltbloomStrikeMana", 4000, 0, 1000000);

            boltbloomSelfStrikeSeconds = builder
                    .comment("In a thunderstorm a Boltbloom under open sky calls harmless lightning onto itself",
                            "about once per this many seconds. 0 = never.")
                    .defineInRange("boltbloomSelfStrikeSeconds", 60, 0, 3600);

            melodiaNoteMana = builder
                    .comment("Melodia mana for a new note (repeats give less).")
                    .defineInRange("melodiaNoteMana", 6, 1, 1000);

            melodiaMaxPerSecond = builder
                    .comment("Melodia cap, mana per second.")
                    .defineInRange("melodiaMaxPerSecond", 30, 1, 10000);

            bumblebloomMana = builder
                    .comment("Bumblebloom mana per pollination.")
                    .defineInRange("bumblebloomMana", 400, 1, 100000);

            bumblebloomCooldownSeconds = builder
                    .comment("How long a Bumblebloom rests after a pollination.")
                    .defineInRange("bumblebloomCooldownSeconds", 40, 0, 3600);

            builder.pop();
        }
    }

    // --- безопасные геттеры ----------------------------------------------
    // Конфиг может быть ещё не загружен (например, на самых ранних этапах
    // запуска) - тогда ForgeConfigSpec кидает IllegalStateException.
    // В этом случае отдаём значения по умолчанию, чтобы ничего не падало.

    public static int manaPerInferium() {
        try {
            return COMMON.manaPerInferium.get();
        } catch (IllegalStateException e) {
            return 2000;
        }
    }

    public static int buyMarkupPercent() {
        try {
            return COMMON.buyMarkupPercent.get();
        } catch (IllegalStateException e) {
            return 125;
        }
    }

    public static boolean enableInsanium() {
        try {
            return COMMON.enableInsanium.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int maxTier() {
        try {
            return COMMON.maxTier.get();
        } catch (IllegalStateException e) {
            return 5;
        }
    }

    public static boolean buyHighestTier() {
        try {
            return COMMON.buyHighestTier.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean automationEnabled() {
        try {
            return COMMON.automationEnabled.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean pullFromContainers() {
        try {
            return COMMON.pullFromContainers.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int pullIntervalTicks() {
        try {
            return COMMON.pullIntervalTicks.get();
        } catch (IllegalStateException e) {
            return 20;
        }
    }

    public static int condenserIntervalTicks() {
        try {
            return COMMON.condenserIntervalTicks.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    public static boolean boostCapacity() {
        try {
            return COMMON.boostCapacity.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean hideLockedInJei() {
        try {
            return COMMON.hideLockedInJei.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int expanderPercent() {
        try {
            return COMMON.expanderPercent.get();
        } catch (IllegalStateException e) {
            return 25;
        }
    }

    public static int poolLinkRate() {
        try {
            return COMMON.poolLinkRate.get();
        } catch (IllegalStateException e) {
            return 50000;
        }
    }

    public static int manaFertilizerStages() {
        try {
            return COMMON.manaFertilizerStages.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    public static int expanderMaxCrystals() {
        try {
            return COMMON.expanderMaxCrystals.get();
        } catch (IllegalStateException e) {
            return 8;
        }
    }

    public static boolean keepManaOnBreak() {
        try {
            return COMMON.keepManaOnBreak.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean privatePools() {
        try {
            return COMMON.privatePools.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static int mysticarnationRange() {
        try {
            return COMMON.mysticarnationRange.get();
        } catch (IllegalStateException e) {
            return 3;
        }
    }

    public static int reaperbloomRange() {
        try {
            return COMMON.reaperbloomRange.get();
        } catch (IllegalStateException e) {
            return 5;
        }
    }

    public static int wardeniaMoistenCost() {
        try {
            return COMMON.wardeniaMoistenCost.get();
        } catch (IllegalStateException e) {
            return 1;
        }
    }

    public static int wardeniaExplosionCost() {
        try {
            return COMMON.wardeniaExplosionCost.get();
        } catch (IllegalStateException e) {
            return 200;
        }
    }

    public static int wardeniaProjectileCost() {
        try {
            return COMMON.wardeniaProjectileCost.get();
        } catch (IllegalStateException e) {
            return 10;
        }
    }

    public static int wardeniaPushCost() {
        try {
            return COMMON.wardeniaPushCost.get();
        } catch (IllegalStateException e) {
            return 20;
        }
    }

    public static int essentidePercent() {
        try {
            return COMMON.essentidePercent.get();
        } catch (IllegalStateException e) {
            return 10;
        }
    }

    public static int brookbellManaPerItem() {
        try {
            return COMMON.brookbellManaPerItem.get();
        } catch (IllegalStateException e) {
            return 5;
        }
    }

    public static int brookbellMaxPerSecond() {
        try {
            return COMMON.brookbellMaxPerSecond.get();
        } catch (IllegalStateException e) {
            return 20;
        }
    }

    public static int boltbloomStrikeMana() {
        try {
            return COMMON.boltbloomStrikeMana.get();
        } catch (IllegalStateException e) {
            return 4000;
        }
    }

    public static int boltbloomSelfStrikeSeconds() {
        try {
            return COMMON.boltbloomSelfStrikeSeconds.get();
        } catch (IllegalStateException e) {
            return 60;
        }
    }

    public static int melodiaNoteMana() {
        try {
            return COMMON.melodiaNoteMana.get();
        } catch (IllegalStateException e) {
            return 6;
        }
    }

    public static int melodiaMaxPerSecond() {
        try {
            return COMMON.melodiaMaxPerSecond.get();
        } catch (IllegalStateException e) {
            return 30;
        }
    }

    public static int bumblebloomMana() {
        try {
            return COMMON.bumblebloomMana.get();
        } catch (IllegalStateException e) {
            return 400;
        }
    }

    public static int bumblebloomCooldownSeconds() {
        try {
            return COMMON.bumblebloomCooldownSeconds.get();
        } catch (IllegalStateException e) {
            return 40;
        }
    }

    public static boolean animateFloatingFlowers() {
        try {
            return COMMON.animateFloatingFlowers.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean poolGlow() {
        try {
            return COMMON.poolGlow.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean showHud() {
        try {
            return COMMON.showHud.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
