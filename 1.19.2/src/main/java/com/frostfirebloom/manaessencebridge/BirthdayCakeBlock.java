package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Праздничный мана-тортик блоком: ставится и съедается за 7 кусков, как
 * ванильный торт. Каждый кусок - праздничные эффекты, доля маны для
 * предметов Botania и достижение (см. BirthdayCakeItem.celebrate).
 *
 * Ванильную ветку со свечами пропускаем: она превратила бы наш торт
 * в обычный ванильный торт со свечой.
 */
public class BirthdayCakeBlock extends CakeBlock {

    public BirthdayCakeBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.CAKE));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            if (player.canEat(false)) {
                return InteractionResult.SUCCESS;
            }
            return player.getItemInHand(hand).isEmpty() ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        boolean hungry = player.canEat(false);
        InteractionResult result = eat(level, pos, state, player);
        if (hungry && result.consumesAction() && player instanceof ServerPlayer) {
            BirthdayCakeItem.celebrate((ServerPlayer) player, pos);
        }
        return result;
    }
}
