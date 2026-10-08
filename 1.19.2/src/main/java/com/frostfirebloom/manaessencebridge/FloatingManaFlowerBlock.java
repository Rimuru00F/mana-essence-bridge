package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Парящий вариант цветка мода - на островке, как парящие цветы Botania.
 *
 * Блок свой: парящий блок Botania - внутренний класс. Островок рисует
 * загрузчик модели Botania ("loader": "botania:floating_flower") по данным
 * блок-сущности цветка. Что цветок парящий, Botania проверяет по тегу блока
 * botania:floating_flowers, а после загрузки мира сама отмечает только свои
 * парящие блоки - наши блок-сущности отмечают себя сами (isFloating). */
public class FloatingManaFlowerBlock extends ManaFlowerBlock {

    private static final VoxelShape SHAPE = Block.box(1.6, 1.6, 1.6, 14.4, 14.4, 14.4);

    public FloatingManaFlowerBlock(Supplier<BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory,
                                   String... tooltip) {
        super(BlockBehaviour.Properties.of(Material.DIRT).strength(0.5F).sound(SoundType.GRAVEL).lightLevel(state -> 15),
                type, factory, tooltip);
    }

    /** Блок-сущность на этом блоке - парящая. */
    public static boolean isFloating(BlockState state) {
        return state.getBlock() instanceof FloatingManaFlowerBlock;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Островок держится сам - земля под ним не нужна. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /** С анимацией блок рисует ManaFlowerRenderer, а не обычная модель. */
    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return BridgeConfig.animateFloatingFlowers() ? net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED
                : net.minecraft.world.level.block.RenderShape.MODEL;
    }

    /**
     * Пчелоцвет на островке: пчела опыляет, зависая на 0,6 блока над низом
     * цветка - внутри островка. Его островок делаем проходимым, иначе пчела
     * не долетит. Без тегов: форму блока игра спрашивает ещё при загрузке,
     * до чтения тегов (на 1.16.5 это краш «Tag used before it was bound»).
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isBumblebloom() ? net.minecraft.world.phys.shapes.Shapes.empty()
                : super.getCollisionShape(state, level, pos, context);
    }

    private boolean isBumblebloom() {
        return ModBlocks.FLOATING_BUMBLEBLOOM.isPresent() && this == ModBlocks.FLOATING_BUMBLEBLOOM.get();
    }
}
