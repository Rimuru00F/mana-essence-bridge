package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import vazkii.botania.api.mana.ManaPool;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Кристалл-расширитель: вплотную к прокачанному пулу (или к другому такому
 * кристаллу, что касается пула) добавляет ему ёмкости - expanderPercent от
 * ёмкости на его тире за кристалл, до expanderMaxCrystals штук. Сам блок
 * ничего не хранит и не тикает: при установке и поломке он только просит
 * соседние пулы пересчитать ёмкость (PoolCapacity.crystals).
 */
public class ExpanderCrystalBlock extends Block {

    /** Сколько кристаллов обходим, разыскивая пулы рядом с группой. */
    private static final int SEARCH_LIMIT = 128;

    public ExpanderCrystalBlock() {
        super(BlockBehaviour.Properties.of(Material.AMETHYST, MaterialColor.COLOR_LIGHT_BLUE)
                .strength(1.5F, 6.0F)
                .sound(SoundType.AMETHYST)
                .lightLevel(state -> 7));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this)) {
            refreshPools(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && !newState.is(this)) {
            refreshPools(level, pos);
        }
    }

    /** Пересчитать ёмкость пулов, которых касается группа кристаллов вокруг pos. */
    private static void refreshPools(Level level, BlockPos pos) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> pools = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos);
        queue.add(pos);
        int crystals = 0;
        while (!queue.isEmpty() && crystals < SEARCH_LIMIT) {
            BlockPos at = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = at.relative(dir);
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockState(next).is(ModBlocks.EXPANDER_CRYSTAL.get())) {
                    queue.add(next);
                    crystals++;
                } else if (level.getBlockEntity(next) instanceof ManaPool) {
                    pools.add(next);
                }
            }
        }
        for (BlockPos poolPos : pools) {
            BlockEntity te = level.getBlockEntity(poolPos);
            InferiumCatalystCapability cap = te == null ? null
                    : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
            if (cap != null && cap.isUpgraded()) {
                PoolCapacity.apply(level, poolPos, te, cap);
                PoolLedger.update(level, poolPos, cap, te);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.manaessencebridge.expander_use",
                BridgeConfig.expanderPercent()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.manaessencebridge.expander_chain",
                BridgeConfig.expanderMaxCrystals()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.manaessencebridge.expander_break")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
