package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;
import vazkii.botania.api.mana.IManaPool;

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
        super(AbstractBlock.Properties.create(Material.ROCK, MaterialColor.LIGHT_BLUE)
                .hardnessAndResistance(1.5F, 6.0F)
                .sound(SoundType.GLASS)
                .setLightLevel(state -> 7)
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0));
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onBlockAdded(state, world, pos, oldState, isMoving);
        if (!world.isRemote && oldState.getBlock() != this) {
            refreshPools(world, pos);
        }
    }

    @Override
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onReplaced(state, world, pos, newState, isMoving);
        if (!world.isRemote && newState.getBlock() != this) {
            refreshPools(world, pos);
        }
    }

    /** Пересчитать ёмкость пулов, которых касается группа кристаллов вокруг pos. */
    private static void refreshPools(World world, BlockPos pos) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> pools = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos);
        queue.add(pos);
        int crystals = 0;
        while (!queue.isEmpty() && crystals < SEARCH_LIMIT) {
            BlockPos at = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = at.offset(dir);
                if (!seen.add(next) || !world.isBlockLoaded(next)) {
                    continue;
                }
                if (world.getBlockState(next).getBlock() == ModBlocks.EXPANDER_CRYSTAL.get()) {
                    queue.add(next);
                    crystals++;
                } else if (world.getTileEntity(next) instanceof IManaPool) {
                    pools.add(next);
                }
            }
        }
        for (BlockPos poolPos : pools) {
            TileEntity te = world.getTileEntity(poolPos);
            InferiumCatalystCapability cap = te == null ? null
                    : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
            if (cap != null && cap.isUpgraded()) {
                PoolCapacity.apply(world, poolPos, te, cap);
                PoolLedger.update(world, poolPos, cap, te);
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable IBlockReader world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.expander_use",
                BridgeConfig.expanderPercent()).mergeStyle(TextFormatting.LIGHT_PURPLE));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.expander_chain",
                BridgeConfig.expanderMaxCrystals()).mergeStyle(TextFormatting.GRAY));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.expander_break")
                .mergeStyle(TextFormatting.DARK_GRAY));
    }
}
