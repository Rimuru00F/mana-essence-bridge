package com.frostfirebloom.manaessencebridge;

import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
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

    INFERIUM(1, "inferium_essence", DyeColor.PURPLE),
    PRUDENTIUM(2, "prudentium_essence", DyeColor.LIME),
    TERTIUM(3, "tertium_essence", DyeColor.LIGHT_BLUE),
    IMPERIUM(4, "imperium_essence", DyeColor.ORANGE),
    SUPREMIUM(5, "supremium_essence", DyeColor.RED);

    public static final String MA_MODID = "mysticalagriculture";

    private final int level;
    private final ResourceLocation essenceId;
    private final DyeColor poolColor;

    EssenceTier(int level, String essencePath, DyeColor poolColor) {
        this.level = level;
        this.essenceId = new ResourceLocation(MA_MODID, essencePath);
        this.poolColor = poolColor;
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
    public ITextComponent getDisplayName() {
        return new TranslationTextComponent(getTranslationKey());
    }

    /** Сколько маны даёт одна эссенция этого тира: база x4 за каждый тир. */
    public int getManaPerEssence() {
        return BridgeConfig.manaPerInferium() << (2 * (level - 1));
    }

    /** Сколько маны стоит выкупить у пула одну эссенцию этого тира. */
    public int getManaCost() {
        return (int) ((long) getManaPerEssence() * BridgeConfig.buyMarkupPercent() / 100L);
    }

    /** Цвет, в который красится прокачанный до этого тира пул. */
    public DyeColor getPoolColor() {
        return poolColor;
    }

    /** Включён ли тир в текущей конфигурации. */
    public boolean isEnabled() {
        return level <= BridgeConfig.maxTier();
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
        if (id == null || !MA_MODID.equals(id.getNamespace())) {
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
