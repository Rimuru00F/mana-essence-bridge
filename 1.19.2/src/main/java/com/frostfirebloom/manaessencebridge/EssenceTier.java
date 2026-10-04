package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Тиры эссенций Mystical Agriculture и их курс обмена на ману.
 *
 * ВАЖНО про баланс: в самой Mystical Agriculture лестница тиров работает
 * ровно 4:1 в ОБЕ стороны (4 эссенции + кристалл -> 1 эссенция следующего
 * тира, и обратно 1 -> 4), а Master Infusion Crystal имеет бесконечную
 * прочность, то есть ходить по лестнице игроку ничего не стоит.
 *
 * Поэтому курс здесь строго умножается на 4 за тир. Любой курс "выгоднее
 * чем x4" превращается в бесконечную ману: докупил эссенций подешевле,
 * бесплатно скрафтил в старший тир, продал дороже. При множителе ровно 4
 * такая прокрутка математически ничего не даёт.
 *
 * Базовая цена тира 1 и наценка на выкуп настраиваются в BridgeConfig.
 */
public enum EssenceTier {

    INFERIUM(1, "inferium_essence", DyeColor.YELLOW, 0x8FAF00),
    PRUDENTIUM(2, "prudentium_essence", DyeColor.LIME, 0x00BA2E),
    TERTIUM(3, "tertium_essence", DyeColor.ORANGE, 0xE25600),
    IMPERIUM(4, "imperium_essence", DyeColor.BLUE, 0x0094FF),
    SUPREMIUM(5, "supremium_essence", DyeColor.RED, 0xE20000),
    /**
     * Шестой тир - только с Mystical Agradditions. Там Инсаниум тоже ходит
     * 4:1 с Супремиумом в обе стороны, так что курс x4 остаётся безопасным.
     */
    INSANIUM(6, EssenceTier.AGRADDITIONS_MODID, "insanium_essence", DyeColor.PURPLE, 0x9B30E8);

    public static final String MA_MODID = "mysticalagriculture";
    public static final String AGRADDITIONS_MODID = "mysticalagradditions";

    private final int level;
    private final ResourceLocation essenceId;
    private final DyeColor poolColor;
    private final int particleColor;

    EssenceTier(int level, String essencePath, DyeColor poolColor, int particleColor) {
        this(level, MA_MODID, essencePath, poolColor, particleColor);
    }

    EssenceTier(int level, String modid, String essencePath, DyeColor poolColor, int particleColor) {
        this.level = level;
        this.essenceId = new ResourceLocation(modid, essencePath);
        this.poolColor = poolColor;
        this.particleColor = particleColor;
    }

    public int getLevel() {
        return level;
    }

    public ResourceLocation getEssenceId() {
        return essenceId;
    }

    /** Ключ локализации названия тира: tier.manaessencebridge.inferium и т.д. */
    public String getTranslationKey() {
        return "tier." + ManaEssenceBridge.MODID + "." + name().toLowerCase(Locale.ROOT);
    }

    /** Название тира как текстовый компонент - переводится уже на клиенте. */
    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }

    /** Сколько маны даёт одна эссенция этого тира: база x4 за каждый тир. */
    public int getManaPerEssence() {
        return BridgeConfig.manaPerInferium() << (2 * (level - 1));
    }

    /** Сколько маны стоит выкупить у пула одну эссенцию этого тира. */
    public int getManaCost() {
        // Считаем в long и зажимаем: на верхних значениях конфига произведение
        // вылезает за int, а отрицательная цена означала бы бесплатную эссенцию
        // и прибавку маны вместо списания.
        long cost = (long) getManaPerEssence() * BridgeConfig.buyMarkupPercent() / 100L;
        return (int) Math.min(cost, Integer.MAX_VALUE);
    }

    /**
     * Цвет частиц при конвертации. Значения сняты пипеткой с настоящих
     * текстур эссенций Mystical Agriculture, поэтому всплеск над пулом
     * совпадает по цвету с тем, что игрок в него кладёт.
     */
    public float particleRed() {
        return ((particleColor >> 16) & 0xFF) / 255.0F;
    }

    public float particleGreen() {
        return ((particleColor >> 8) & 0xFF) / 255.0F;
    }

    public float particleBlue() {
        return (particleColor & 0xFF) / 255.0F;
    }

    /** Цвет, в который красится прокачанный до этого тира пул. */
    public DyeColor getPoolColor() {
        return poolColor;
    }

    /** Включён ли тир в текущей конфигурации. */
    public boolean isEnabled() {
        if (this == INSANIUM) {
            return BridgeConfig.enableInsanium() && BridgeConfig.maxTier() >= SUPREMIUM.level
                    && agradditionsLoaded();
        }
        return level <= BridgeConfig.maxTier();
    }

    /** Стоит ли Mystical Agradditions - без неё шестого тира нет вовсе. */
    public static boolean agradditionsLoaded() {
        return net.minecraftforge.fml.ModList.get() != null
                && net.minecraftforge.fml.ModList.get().isLoaded(AGRADDITIONS_MODID);
    }

    /** Старший включённый тир (с учётом Инсаниума). */
    public static int maxEnabledLevel() {
        return INSANIUM.isEnabled() ? INSANIUM.level : Math.min(BridgeConfig.maxTier(), SUPREMIUM.level);
    }

    /** Название катализатора, открывающего этот тир. */
    public String getCatalystRegistryName() {
        return name().toLowerCase(Locale.ROOT) + "_mana_catalyst";
    }

    /** Предмет-эссенция из Mystical Agriculture; null, если мода нет в сборке. */
    public Item getEssenceItem() {
        return ForgeRegistries.ITEMS.getValue(essenceId);
    }

    public static EssenceTier byLevel(int level) {
        for (EssenceTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return null;
    }

    /** Определяет тир по предмету в руке; null - если это не эссенция MA. */
    public static EssenceTier fromItem(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !(MA_MODID.equals(id.getNamespace()) || AGRADDITIONS_MODID.equals(id.getNamespace()))) {
            return null;
        }
        for (EssenceTier tier : values()) {
            if (tier.essenceId.equals(id)) {
                return tier;
            }
        }
        return null;
    }
}
