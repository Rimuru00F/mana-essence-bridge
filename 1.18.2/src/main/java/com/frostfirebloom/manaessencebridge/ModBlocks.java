package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Блоки мода и их блок-сущности. Пока он один - Конденсатор эссенции. */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ManaEssenceBridge.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, ManaEssenceBridge.MODID);

    public static final RegistryObject<Block> ESSENCE_CONDENSER =
            BLOCKS.register("essence_condenser", EssenceCondenserBlock::new);

    @SuppressWarnings("ConstantConditions") // тип данных не нужен: блок-сущность не входит в структуры
    public static final RegistryObject<BlockEntityType<EssenceCondenserBlockEntity>> ESSENCE_CONDENSER_BE =
            BLOCK_ENTITIES.register("essence_condenser", () -> BlockEntityType.Builder
                    .of(EssenceCondenserBlockEntity::new, ESSENCE_CONDENSER.get()).build(null));

    // --- функциональные цветки --------------------------------------------

    public static final RegistryObject<Block> MYSTICARNATION = BLOCKS.register("mysticarnation",
            () -> new ManaFlowerBlock(() -> ModBlocks.MYSTICARNATION_BE.get(), MysticarnationBlockEntity::new,
                    "tooltip.manaessencebridge.mysticarnation", "tooltip.manaessencebridge.mysticarnation_limit",
                    "tooltip.manaessencebridge.flower_pool"));

    public static final RegistryObject<Block> FLOATING_MYSTICARNATION = BLOCKS.register("floating_mysticarnation",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.MYSTICARNATION_BE.get(), MysticarnationBlockEntity::new,
                    "tooltip.manaessencebridge.mysticarnation", "tooltip.manaessencebridge.mysticarnation_limit",
                    "tooltip.manaessencebridge.flower_pool"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<MysticarnationBlockEntity>> MYSTICARNATION_BE =
            BLOCK_ENTITIES.register("mysticarnation", () -> BlockEntityType.Builder
                    .of(MysticarnationBlockEntity::new, MYSTICARNATION.get(), FLOATING_MYSTICARNATION.get()).build(null));

    public static final RegistryObject<Block> REAPERBLOOM = BLOCKS.register("reaperbloom",
            () -> new ManaFlowerBlock(() -> ModBlocks.REAPERBLOOM_BE.get(), ReaperbloomBlockEntity::new,
                    "tooltip.manaessencebridge.reaperbloom", "tooltip.manaessencebridge.flower_pool"));

    public static final RegistryObject<Block> FLOATING_REAPERBLOOM = BLOCKS.register("floating_reaperbloom",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.REAPERBLOOM_BE.get(), ReaperbloomBlockEntity::new,
                    "tooltip.manaessencebridge.reaperbloom", "tooltip.manaessencebridge.flower_pool"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ReaperbloomBlockEntity>> REAPERBLOOM_BE =
            BLOCK_ENTITIES.register("reaperbloom", () -> BlockEntityType.Builder
                    .of(ReaperbloomBlockEntity::new, REAPERBLOOM.get(), FLOATING_REAPERBLOOM.get()).build(null));

    public static final RegistryObject<Block> WARDENIA = BLOCKS.register("wardenia",
            () -> new ManaFlowerBlock(() -> ModBlocks.WARDENIA_BE.get(), WardeniaBlockEntity::new,
                    "tooltip.manaessencebridge.wardenia", "tooltip.manaessencebridge.flower_pool"));

    public static final RegistryObject<Block> FLOATING_WARDENIA = BLOCKS.register("floating_wardenia",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.WARDENIA_BE.get(), WardeniaBlockEntity::new,
                    "tooltip.manaessencebridge.wardenia", "tooltip.manaessencebridge.flower_pool"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<WardeniaBlockEntity>> WARDENIA_BE =
            BLOCK_ENTITIES.register("wardenia", () -> BlockEntityType.Builder
                    .of(WardeniaBlockEntity::new, WARDENIA.get(), FLOATING_WARDENIA.get()).build(null));

    public static final RegistryObject<Block> ESSENTIDE = BLOCKS.register("essentide",
            () -> new ManaFlowerBlock(() -> ModBlocks.ESSENTIDE_BE.get(), EssentideBlockEntity::new,
                    "tooltip.manaessencebridge.essentide", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_ESSENTIDE = BLOCKS.register("floating_essentide",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.ESSENTIDE_BE.get(), EssentideBlockEntity::new,
                    "tooltip.manaessencebridge.essentide", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<EssentideBlockEntity>> ESSENTIDE_BE =
            BLOCK_ENTITIES.register("essentide", () -> BlockEntityType.Builder
                    .of(EssentideBlockEntity::new, ESSENTIDE.get(), FLOATING_ESSENTIDE.get()).build(null));

    public static final RegistryObject<Block> BROOKBELL = BLOCKS.register("brookbell",
            () -> new ManaFlowerBlock(() -> ModBlocks.BROOKBELL_BE.get(), BrookbellBlockEntity::new,
                    "tooltip.manaessencebridge.brookbell", "tooltip.manaessencebridge.brookbell_limit", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_BROOKBELL = BLOCKS.register("floating_brookbell",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.BROOKBELL_BE.get(), BrookbellBlockEntity::new,
                    "tooltip.manaessencebridge.brookbell", "tooltip.manaessencebridge.brookbell_limit", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BrookbellBlockEntity>> BROOKBELL_BE =
            BLOCK_ENTITIES.register("brookbell", () -> BlockEntityType.Builder
                    .of(BrookbellBlockEntity::new, BROOKBELL.get(), FLOATING_BROOKBELL.get()).build(null));

    public static final RegistryObject<Block> BOLTBLOOM = BLOCKS.register("boltbloom",
            () -> new ManaFlowerBlock(() -> ModBlocks.BOLTBLOOM_BE.get(), BoltbloomBlockEntity::new,
                    "tooltip.manaessencebridge.boltbloom", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_BOLTBLOOM = BLOCKS.register("floating_boltbloom",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.BOLTBLOOM_BE.get(), BoltbloomBlockEntity::new,
                    "tooltip.manaessencebridge.boltbloom", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BoltbloomBlockEntity>> BOLTBLOOM_BE =
            BLOCK_ENTITIES.register("boltbloom", () -> BlockEntityType.Builder
                    .of(BoltbloomBlockEntity::new, BOLTBLOOM.get(), FLOATING_BOLTBLOOM.get()).build(null));

    public static final RegistryObject<Block> MELODIA = BLOCKS.register("melodia",
            () -> new ManaFlowerBlock(() -> ModBlocks.MELODIA_BE.get(), MelodiaBlockEntity::new,
                    "tooltip.manaessencebridge.melodia", "tooltip.manaessencebridge.melodia_limit", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_MELODIA = BLOCKS.register("floating_melodia",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.MELODIA_BE.get(), MelodiaBlockEntity::new,
                    "tooltip.manaessencebridge.melodia", "tooltip.manaessencebridge.melodia_limit", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<MelodiaBlockEntity>> MELODIA_BE =
            BLOCK_ENTITIES.register("melodia", () -> BlockEntityType.Builder
                    .of(MelodiaBlockEntity::new, MELODIA.get(), FLOATING_MELODIA.get()).build(null));

    public static final RegistryObject<Block> BUMBLEBLOOM = BLOCKS.register("bumblebloom",
            () -> new ManaFlowerBlock(() -> ModBlocks.BUMBLEBLOOM_BE.get(), BumblebloomBlockEntity::new,
                    "tooltip.manaessencebridge.bumblebloom", "tooltip.manaessencebridge.flower_spreader"));

    public static final RegistryObject<Block> FLOATING_BUMBLEBLOOM = BLOCKS.register("floating_bumblebloom",
            () -> new FloatingManaFlowerBlock(() -> ModBlocks.BUMBLEBLOOM_BE.get(), BumblebloomBlockEntity::new,
                    "tooltip.manaessencebridge.bumblebloom", "tooltip.manaessencebridge.flower_spreader"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BumblebloomBlockEntity>> BUMBLEBLOOM_BE =
            BLOCK_ENTITIES.register("bumblebloom", () -> BlockEntityType.Builder
                    .of(BumblebloomBlockEntity::new, BUMBLEBLOOM.get(), FLOATING_BUMBLEBLOOM.get()).build(null));

    /** Кристалл-расширитель: добавляет ёмкости соседнему прокачанному пулу. */
    public static final RegistryObject<Block> EXPANDER_CRYSTAL =
            BLOCKS.register("expander_crystal", ExpanderCrystalBlock::new);

    /** Праздничный мана-тортик (пасхалка 17 октября) - блок без блок-сущности, как ванильный торт. */
    public static final RegistryObject<Block> BIRTHDAY_CAKE =
            BLOCKS.register("birthday_cake", BirthdayCakeBlock::new);

    private ModBlocks() {
    }
}
