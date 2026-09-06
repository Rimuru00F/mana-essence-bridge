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
        public final ForgeConfigSpec.BooleanValue buyHighestTier;
        public final ForgeConfigSpec.BooleanValue automationEnabled;
        public final ForgeConfigSpec.BooleanValue hideLockedInJei;
        public final ForgeConfigSpec.BooleanValue showHud;

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

            hideLockedInJei = builder
                    .comment(
                            "Hide catalysts in JEI until their Botania gate is passed",
                            "(Terrasteel / Alfheim portal / Gaia Guardian).")
                    .define("hideLockedInJei", true);

            showHud = builder
                    .comment("Show tier and exchange rate when looking at an upgraded pool.")
                    .define("showHud", true);

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

    public static boolean hideLockedInJei() {
        try {
            return COMMON.hideLockedInJei.get();
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
