package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import vazkii.botania.api.subtile.TileEntitySpecialFlower;

import javax.annotation.Nullable;
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

    private static final VoxelShape SHAPE = makeCuboidShape(1.6, 1.6, 1.6, 14.4, 14.4, 14.4);

    public FloatingManaFlowerBlock(Supplier<? extends TileEntity> factory, String... tooltip) {
        super(AbstractBlock.Properties.create(Material.EARTH).hardnessAndResistance(0.5F)
                .sound(SoundType.GROUND).setLightLevel(state -> 15), factory, tooltip);
    }

    /** Блок-сущность на этом блоке - парящая. */
    public static boolean isFloating(BlockState state) {
        return state.getBlock() instanceof FloatingManaFlowerBlock;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    /** Островок держится сам - земля под ним не нужна. */
    @SuppressWarnings("deprecation")
    @Override
    public boolean isValidPosition(BlockState state, IWorldReader world, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        TileEntity te = super.createTileEntity(state, world);
        if (te instanceof TileEntitySpecialFlower) {
            ((TileEntitySpecialFlower) te).setFloating(true);
        }
        return te;
    }

    /** С анимацией блок рисует ManaFlowerRenderer, а не обычная модель. */
    @SuppressWarnings("deprecation")
    @Override
    public net.minecraft.block.BlockRenderType getRenderType(BlockState state) {
        return BridgeConfig.animateFloatingFlowers() ? net.minecraft.block.BlockRenderType.ENTITYBLOCK_ANIMATED
                : net.minecraft.block.BlockRenderType.MODEL;
    }

    /** Островок стоит ровно по центру блока. */
    @Override
    public AbstractBlock.OffsetType getOffsetType() {
        return AbstractBlock.OffsetType.NONE;
    }

    /**
     * Пчелоцвет на островке: пчела опыляет, зависая на 0,6 блока над низом
     * цветка - внутри островка. Его островок делаем проходимым, иначе пчела
     * не долетит. Без тегов: форму блока игра спрашивает ещё при загрузке,
     * до чтения тегов (на 1.16.5 это краш «Tag used before it was bound»).
     */
    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return isBumblebloom() ? net.minecraft.util.math.shapes.VoxelShapes.empty()
                : super.getCollisionShape(state, world, pos, context);
    }

    private boolean isBumblebloom() {
        return ModBlocks.FLOATING_BUMBLEBLOOM.isPresent() && this == ModBlocks.FLOATING_BUMBLEBLOOM.get();
    }
}
