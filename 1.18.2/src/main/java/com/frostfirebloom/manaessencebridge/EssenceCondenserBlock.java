package com.frostfirebloom.manaessencebridge;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Конденсатор эссенции: сам выкупает у прокачанного пула эссенцию за ману.
 *
 * Вторая половина моста для автоматики. Эссенция в ману и так идёт сама
 * (воронки, трубы, сифон), а обратно - только ручным кликом. Конденсатор
 * берёт ману из пула по обычной цене выкупа и складывает эссенцию в себя,
 * откуда её забирают воронки и трубы.
 */
public class EssenceCondenserBlock extends BaseEntityBlock {

    public EssenceCondenserBlock() {
        super(BlockBehaviour.Properties.of(Material.STONE, MaterialColor.QUARTZ)
                .strength(2.0F, 10.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
    }

    /** BaseEntityBlock по умолчанию невидим - рисуем обычной моделью блока. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssenceCondenserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlocks.ESSENCE_CONDENSER_BE.get(), EssenceCondenserBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        EssenceTier tier = EssenceTier.fromItem(held.getItem());
        // С любым другим предметом - прежде всего с жезлом Botania для привязки -
        // отдаём клик самому предмету.
        if (tier == null && !held.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof EssenceCondenserBlockEntity)) {
            return InteractionResult.PASS;
        }
        EssenceCondenserBlockEntity condenser = (EssenceCondenserBlockEntity) be;
        if (tier != null) {
            condenser.setFilter(player, tier);
        } else if (player.isShiftKeyDown()) {
            condenser.cycleReserve(player);
        } else {
            condenser.handOut(player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof EssenceCondenserBlockEntity ? ((EssenceCondenserBlockEntity) be).comparatorSignal() : 0;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof EssenceCondenserBlockEntity) {
                ((EssenceCondenserBlockEntity) be).dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(new TranslatableComponent("tooltip.manaessencebridge.condenser_what").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(new TranslatableComponent("tooltip.manaessencebridge.condenser_how").withStyle(ChatFormatting.GRAY));
        tooltip.add(new TranslatableComponent("tooltip.manaessencebridge.condenser_bind").withStyle(ChatFormatting.GRAY));
    }

    /** Достижение «Конденсатор ожил» - тому, кто поставил блок. */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                            net.minecraft.world.level.block.state.BlockState state,
                            @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (placer != null && be instanceof EssenceCondenserBlockEntity) {
            ((EssenceCondenserBlockEntity) be).setOwner(placer.getUUID());
        }
        if (placer instanceof net.minecraft.server.level.ServerPlayer) {
            PoolThroughput.award((net.minecraft.server.level.ServerPlayer) placer, "condenser_set");
        }
    }
}
