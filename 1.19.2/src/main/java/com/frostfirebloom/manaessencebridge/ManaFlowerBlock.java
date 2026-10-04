package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Блок функционального цветка мода.
 *
 * Свой, на основе ванильного куста: блок цветка у Botania - внутренний класс,
 * а мы держимся публичного API. Вся логика живёт в блок-сущности - наследнике
 * FunctionalFlowerBlockEntity из API, которая сама находит пул, тянет из него
 * ману, привязывается Жезлом леса и слушает редстоун.
 */
public class ManaFlowerBlock extends BushBlock implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(4.8, 0, 4.8, 11.2, 12.8, 11.2);

    private final Supplier<BlockEntityType<?>> type;
    private final BiFunction<BlockPos, BlockState, BlockEntity> factory;
    private final String[] tooltip;

    public ManaFlowerBlock(Supplier<BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory,
                           String... tooltip) {
        super(BlockBehaviour.Properties.of(Material.PLANT).noCollission().instabreak().sound(SoundType.GRASS));
        this.type = type;
        this.factory = factory;
        this.tooltip = tooltip;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != this.type.get()) {
            return null;
        }
        return (l, p, s, be) -> SpecialFlowerBlockEntity.commonTick(l, p, s, (SpecialFlowerBlockEntity) be);
    }

    /** Как у цветков Botania: при посадке цветок сразу ищет ближайший пул. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SpecialFlowerBlockEntity) {
            ((SpecialFlowerBlockEntity) be).setPlacedBy(level, pos, state, placer, stack);
        }
        if (placer instanceof net.minecraft.server.level.ServerPlayer) {
            // «Ферма на цветках»: у достижения по критерию на каждый цветок мода
            String flower = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(this).getPath();
            PoolThroughput.award((net.minecraft.server.level.ServerPlayer) placer, "auto_farm", flower);
            // «Садовник Botania»: по критерию на каждый из восьми цветков мода
            PoolThroughput.award((net.minecraft.server.level.ServerPlayer) placer, "gardener", flower);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> lines, TooltipFlag flag) {
        for (int i = 0; i < tooltip.length; i++) {
            lines.add(Component.translatable(tooltip[i]).withStyle(i == 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        }
    }
}
