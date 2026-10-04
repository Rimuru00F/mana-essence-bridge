package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

/**
 * Праздничный мана-тортик блоком: ставится и съедается за 7 кусков, как
 * ванильный торт, - и так же только когда игрок голоден. Каждый кусок -
 * праздничные эффекты, мана в ближайший пул (чанк торта и соседние) и достижение
 * (см. BirthdayCakeItem.celebrate).
 */
public class BirthdayCakeBlock extends CakeBlock {

    public BirthdayCakeBlock() {
        super(AbstractBlock.Properties.from(Blocks.CAKE));
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                             BlockRayTraceResult hit) {
        boolean hungry = player.canEat(false);
        ActionResultType result = super.onBlockActivated(state, world, pos, player, hand, hit);
        if (!world.isRemote && hungry && result.isSuccessOrConsume() && player instanceof ServerPlayerEntity) {
            BirthdayCakeItem.celebrate((ServerPlayerEntity) player, pos);
        }
        return result;
    }
}
