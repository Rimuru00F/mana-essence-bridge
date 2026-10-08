package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Блоки мода и их блок-сущности. Пока он один - Конденсатор эссенции. */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ManaEssenceBridge.MODID);

    public static final DeferredRegister<TileEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, ManaEssenceBridge.MODID);

    public static final RegistryObject<Block> ESSENCE_CONDENSER =
            BLOCKS.register("essence_condenser", EssenceCondenserBlock::new);

    @SuppressWarnings("ConstantConditions") // тип данных не нужен: блок-сущность не входит в структуры
    public static final RegistryObject<TileEntityType<EssenceCondenserBlockEntity>> ESSENCE_CONDENSER_BE =
            BLOCK_ENTITIES.register("essence_condenser", () -> TileEntityType.Builder
                    .create(EssenceCondenserBlockEntity::new, ESSENCE_CONDENSER.get()).build(null));

    // --- функциональные цветки --------------------------------------------

    public static final RegistryObject<Block> MYSTICARNATION = BLOCKS.register("mysticarnation",
            () -> new ManaFlowerBlock(MysticarnationBlockEntity::new,
                    "tooltip.manaessencebridge.mysticarnation", "tooltip.manaessencebridge.mysticarnation_limit",
                    "tooltip.manaessencebridge.flower_pool"));

    public static final RegistryObject<Block> FLOATING_MYSTICARNATION = BLOCKS.register("floating_mysticarnation",
            () -> new FloatingManaFlowerBlock(MysticarnationBlockEntity::new,
                    "tooltip.manaessencebridge.mysticarnation", "tooltip.manaessencebridge.mysticarnation_limit",
                    "tooltip.manaessencebridge.flower_pool"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<MysticarnationBlockEntity>> MYSTICARNATION_BE =
            BLOCK_ENTITIES.register("mysticarnation", () -> TileEntityType.Builder
                    .create(MysticarnationBlockEntity::new, MYSTICARNATION.get(), FLOATING_MYSTICARNATION.get()).build(null));

    public static final RegistryObject<Block> REAPERBLOOM = BLOCKS.register("reaperbloom",
            () -> new ManaFlowerBlock(ReaperbloomBlockEntity::new,
                    "tooltip.manaessencebridge.reaperbloom", "tooltip.manaessencebridge.flower_pool"));

    public static final RegistryObject<Block> FLOATING_REAPERBLOOM = BLOCKS.register("floating_reaperbloom",
            () -> new FloatingManaFlowerBlock(ReaperbloomBlockEntity::new,
                    "tooltip.manaessencebridge.reaperbloom", "tooltip.manaessencebridge.flower_pool"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<ReaperbloomBlockEntity>> REAPERBLOOM_BE =
            BLOCK_ENTITIES.register("reaperbloom", () -> TileEntityType.Builder
                    .create(ReaperbloomBlockEntity::new, REAPERBLOOM.get(), FLOATING_REAPERBLOOM.get()).build(null));

    public static final RegistryObject<Block> WARDENIA = BLOCKS.register("wardenia",
            () -> new ManaFlowerBlock(WardeniaBlockEntity::new,
                    "tooltip.manaessencebridge.wardenia", "tooltip.manaessencebridge.flower_pool"));

    public static final RegistryObject<Block> FLOATING_WARDENIA = BLOCKS.register("floating_wardenia",
            () -> new FloatingManaFlowerBlock(WardeniaBlockEntity::new,
                    "tooltip.manaessencebridge.wardenia", "tooltip.manaessencebridge.flower_pool"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<WardeniaBlockEntity>> WARDENIA_BE =
            BLOCK_ENTITIES.register("wardenia", () -> TileEntityType.Builder
                    .create(WardeniaBlockEntity::new, WARDENIA.get(), FLOATING_WARDENIA.get()).build(null));

    public static final RegistryObject<Block> ESSENTIDE = BLOCKS.register("essentide",
            () -> new ManaFlowerBlock(EssentideBlockEntity::new,
                    "tooltip.manaessencebridge.essentide", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_ESSENTIDE = BLOCKS.register("floating_essentide",
            () -> new FloatingManaFlowerBlock(EssentideBlockEntity::new,
                    "tooltip.manaessencebridge.essentide", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<EssentideBlockEntity>> ESSENTIDE_BE =
            BLOCK_ENTITIES.register("essentide", () -> TileEntityType.Builder
                    .create(EssentideBlockEntity::new, ESSENTIDE.get(), FLOATING_ESSENTIDE.get()).build(null));

    public static final RegistryObject<Block> BROOKBELL = BLOCKS.register("brookbell",
            () -> new ManaFlowerBlock(BrookbellBlockEntity::new,
                    "tooltip.manaessencebridge.brookbell", "tooltip.manaessencebridge.brookbell_limit", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_BROOKBELL = BLOCKS.register("floating_brookbell",
            () -> new FloatingManaFlowerBlock(BrookbellBlockEntity::new,
                    "tooltip.manaessencebridge.brookbell", "tooltip.manaessencebridge.brookbell_limit", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<BrookbellBlockEntity>> BROOKBELL_BE =
            BLOCK_ENTITIES.register("brookbell", () -> TileEntityType.Builder
                    .create(BrookbellBlockEntity::new, BROOKBELL.get(), FLOATING_BROOKBELL.get()).build(null));

    public static final RegistryObject<Block> BOLTBLOOM = BLOCKS.register("boltbloom",
            () -> new ManaFlowerBlock(BoltbloomBlockEntity::new,
                    "tooltip.manaessencebridge.boltbloom", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_BOLTBLOOM = BLOCKS.register("floating_boltbloom",
            () -> new FloatingManaFlowerBlock(BoltbloomBlockEntity::new,
                    "tooltip.manaessencebridge.boltbloom", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<BoltbloomBlockEntity>> BOLTBLOOM_BE =
            BLOCK_ENTITIES.register("boltbloom", () -> TileEntityType.Builder
                    .create(BoltbloomBlockEntity::new, BOLTBLOOM.get(), FLOATING_BOLTBLOOM.get()).build(null));

    public static final RegistryObject<Block> MELODIA = BLOCKS.register("melodia",
            () -> new ManaFlowerBlock(MelodiaBlockEntity::new,
                    "tooltip.manaessencebridge.melodia", "tooltip.manaessencebridge.melodia_limit", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_MELODIA = BLOCKS.register("floating_melodia",
            () -> new FloatingManaFlowerBlock(MelodiaBlockEntity::new,
                    "tooltip.manaessencebridge.melodia", "tooltip.manaessencebridge.melodia_limit", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<MelodiaBlockEntity>> MELODIA_BE =
            BLOCK_ENTITIES.register("melodia", () -> TileEntityType.Builder
                    .create(MelodiaBlockEntity::new, MELODIA.get(), FLOATING_MELODIA.get()).build(null));

    public static final RegistryObject<Block> BUMBLEBLOOM = BLOCKS.register("bumblebloom",
            () -> new ManaFlowerBlock(BumblebloomBlockEntity::new,
                    "tooltip.manaessencebridge.bumblebloom", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_BUMBLEBLOOM = BLOCKS.register("floating_bumblebloom",
            () -> new FloatingManaFlowerBlock(BumblebloomBlockEntity::new,
                    "tooltip.manaessencebridge.bumblebloom", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<TileEntityType<BumblebloomBlockEntity>> BUMBLEBLOOM_BE =
            BLOCK_ENTITIES.register("bumblebloom", () -> TileEntityType.Builder
                    .create(BumblebloomBlockEntity::new, BUMBLEBLOOM.get(), FLOATING_BUMBLEBLOOM.get()).build(null));

    /** Кристалл-расширитель: добавляет ёмкости соседнему прокачанному пулу. */
    public static final RegistryObject<Block> EXPANDER_CRYSTAL =
            BLOCKS.register("expander_crystal", ExpanderCrystalBlock::new);

    /** Праздничный мана-тортик (пасхалка 17 октября) - блок без блок-сущности, как ванильный торт. */
    public static final RegistryObject<Block> BIRTHDAY_CAKE =
            BLOCKS.register("birthday_cake", BirthdayCakeBlock::new);

    private ModBlocks() {
    }
}
