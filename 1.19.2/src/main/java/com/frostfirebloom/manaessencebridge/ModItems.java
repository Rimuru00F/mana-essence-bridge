package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
                    () -> new net.minecraft.world.item.BlockItem(ModBlocks.EXPANDER_CRYSTAL.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    /** Праздничный мана-тортик: подарок 17 октября, во вкладках креатива его нет. */
    public static final RegistryObject<Item> BIRTHDAY_CAKE =
            ITEMS.register("birthday_cake", () -> new BirthdayCakeItem(ModBlocks.BIRTHDAY_CAKE.get()));

    /** Предмет-блок Конденсатора эссенции. */
    public static final RegistryObject<Item> ESSENCE_CONDENSER =
            ITEMS.register("essence_condenser",
                    () -> new net.minecraft.world.item.BlockItem(ModBlocks.ESSENCE_CONDENSER.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    /** Предметы-блоки функциональных цветков. */
    public static final RegistryObject<Item> MYSTICARNATION = ITEMS.register("mysticarnation",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.MYSTICARNATION.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> REAPERBLOOM = ITEMS.register("reaperbloom",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.REAPERBLOOM.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> WARDENIA = ITEMS.register("wardenia",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.WARDENIA.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> ESSENTIDE = ITEMS.register("essentide",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.ESSENTIDE.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> BROOKBELL = ITEMS.register("brookbell",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.BROOKBELL.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> BOLTBLOOM = ITEMS.register("boltbloom",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.BOLTBLOOM.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> MELODIA = ITEMS.register("melodia",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.MELODIA.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> BUMBLEBLOOM = ITEMS.register("bumblebloom",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.BUMBLEBLOOM.get(), new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC)));

    public static RegistryObject<Item> getCatalyst(EssenceTier tier) {
        return CATALYSTS.get(tier);
    }

    // В 1.19.2 вкладка креатива задаётся прямо в Item.Properties -
    // см. CatalystItem и ResonanceCrystalItem.
}
