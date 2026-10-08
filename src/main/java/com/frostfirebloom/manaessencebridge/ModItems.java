package com.frostfirebloom.manaessencebridge;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ManaEssenceBridge.MODID);

    /**
     * По катализатору на каждый тир эссенции. Каждый следующий крафтится
     * из предыдущего, так что прокачка пула идёт строго по порядку.
     */
    private static final Map<EssenceTier, RegistryObject<Item>> CATALYSTS = new EnumMap<>(EssenceTier.class);

    static {
        for (EssenceTier tier : EssenceTier.values()) {
            CATALYSTS.put(tier, ITEMS.register(tier.getCatalystRegistryName(), () -> new CatalystItem(tier)));
        }
    }

    /** Резонансный кристалл - снимает с пула верхний тир. */
    public static final RegistryObject<Item> RESONANCE_CRYSTAL =
            ITEMS.register("resonance_crystal", ResonanceCrystalItem::new);

    /** Сифон эссенции - включает и выключает автозабор из сундуков. */
    public static final RegistryObject<Item> ESSENCE_SIPHON =
            ITEMS.register("essence_siphon", EssenceSiphonItem::new);

    /** Мана-гроссбух: список своих прокачанных пулов. */
    public static final RegistryObject<Item> MANA_LEDGER =
            ITEMS.register("mana_ledger", ManaLedgerItem::new);

    /** Линза конденсации для распределителя маны. */
    public static final RegistryObject<Item> CONDENSATION_LENS =
            ITEMS.register("condensation_lens", CondensationLensItem::new);

    /** Зеркало эссенции: выкуп и продажа через привязанный пул откуда угодно в измерении. */
    public static final RegistryObject<Item> ESSENCE_MIRROR =
            ITEMS.register("essence_mirror", EssenceMirrorItem::new);

    /** Предмет-блок Кристалла-расширителя. */
    public static final RegistryObject<Item> EXPANDER_CRYSTAL =
            ITEMS.register("expander_crystal",
                    () -> new net.minecraft.item.BlockItem(ModBlocks.EXPANDER_CRYSTAL.get(),
                            new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    /** Пояс Стражении: защита цветка вокруг игрока, слот пояса Curios. */
    public static final RegistryObject<Item> WARDENIA_BELT =
            ITEMS.register("wardenia_belt", WardeniaBeltItem::new);

    /** Мана-удобрение: Fertilized Essence, настоянная в пуле. */
    public static final RegistryObject<Item> MANA_FERTILIZER =
            ITEMS.register("mana_fertilizer", ManaFertilizerItem::new);

    /** Супер мана-удобрение: 5x5, сразу до спелости. */
    public static final RegistryObject<Item> GREATER_MANA_FERTILIZER = ITEMS.register("greater_mana_fertilizer",
            () -> new ManaFertilizerItem(2, true, "tooltip.manaessencebridge.greater_fertilizer_use"));

    /** Праздничный мана-тортик: подарок 17 октября, во вкладках креатива его нет. */
    public static final RegistryObject<Item> BIRTHDAY_CAKE =
            ITEMS.register("birthday_cake", () -> new BirthdayCakeItem(ModBlocks.BIRTHDAY_CAKE.get()));

    /** Предмет-блок Конденсатора эссенции. */
    public static final RegistryObject<Item> ESSENCE_CONDENSER =
            ITEMS.register("essence_condenser",
                    () -> new net.minecraft.item.BlockItem(ModBlocks.ESSENCE_CONDENSER.get(),
                            new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    /** Предметы-блоки функциональных цветков. */
    public static final RegistryObject<Item> MYSTICARNATION = ITEMS.register("mysticarnation",
            () -> new net.minecraft.item.BlockItem(ModBlocks.MYSTICARNATION.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_MYSTICARNATION = ITEMS.register("floating_mysticarnation",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_MYSTICARNATION.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> REAPERBLOOM = ITEMS.register("reaperbloom",
            () -> new net.minecraft.item.BlockItem(ModBlocks.REAPERBLOOM.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_REAPERBLOOM = ITEMS.register("floating_reaperbloom",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_REAPERBLOOM.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> WARDENIA = ITEMS.register("wardenia",
            () -> new net.minecraft.item.BlockItem(ModBlocks.WARDENIA.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_WARDENIA = ITEMS.register("floating_wardenia",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_WARDENIA.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> ESSENTIDE = ITEMS.register("essentide",
            () -> new net.minecraft.item.BlockItem(ModBlocks.ESSENTIDE.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_ESSENTIDE = ITEMS.register("floating_essentide",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_ESSENTIDE.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> BROOKBELL = ITEMS.register("brookbell",
            () -> new net.minecraft.item.BlockItem(ModBlocks.BROOKBELL.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_BROOKBELL = ITEMS.register("floating_brookbell",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_BROOKBELL.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> BOLTBLOOM = ITEMS.register("boltbloom",
            () -> new net.minecraft.item.BlockItem(ModBlocks.BOLTBLOOM.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_BOLTBLOOM = ITEMS.register("floating_boltbloom",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_BOLTBLOOM.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> MELODIA = ITEMS.register("melodia",
            () -> new net.minecraft.item.BlockItem(ModBlocks.MELODIA.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_MELODIA = ITEMS.register("floating_melodia",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_MELODIA.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> BUMBLEBLOOM = ITEMS.register("bumblebloom",
            () -> new net.minecraft.item.BlockItem(ModBlocks.BUMBLEBLOOM.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static final RegistryObject<Item> FLOATING_BUMBLEBLOOM = ITEMS.register("floating_bumblebloom",
            () -> new net.minecraft.item.BlockItem(ModBlocks.FLOATING_BUMBLEBLOOM.get(),
                    new Item.Properties().group(net.minecraft.item.ItemGroup.MISC)));

    public static RegistryObject<Item> getCatalyst(EssenceTier tier) {
        return CATALYSTS.get(tier);
    }
}
