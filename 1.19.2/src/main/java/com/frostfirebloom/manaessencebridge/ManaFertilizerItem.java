package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Мана-удобрение: Fertilized Essence, настоянная в пуле маны (45 000 маны).
 * ПКМ по культуре Mystical Agriculture - все культуры MA в квадрате 3x3
 * вокруг неё подрастают на manaFertilizerStages стадий (по умолчанию 2).
 * Работает и из раздатчика. Обычные культуры не трогает.
 *
 * Баланс: полный рост (7 стадий) - 4 удобрения на 9 культур, то есть
 * 20 000 маны на культуру. Это дороже двух эссенций шестого тира у Эссентиды
 * (12 800) и не дешевле Мистикарнации - петли бесконечной маны нет.
 */
public class ManaFertilizerItem extends Item {

    /** Радиус квадрата: 1 - 3x3, 2 - 5x5. */
    private final int radius;
    /** true - сразу до спелости, false - на manaFertilizerStages стадий. */
    private final boolean full;
    private final String useKey;

    public ManaFertilizerItem() {
        this(1, false, "tooltip.manaessencebridge.fertilizer_use");
    }

    public ManaFertilizerItem(int radius, boolean full, String useKey) {
        super(new Item.Properties().tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC));
        this.radius = radius;
        this.full = full;
        this.useKey = useKey;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = target(level, context.getClickedPos());
        Player player = context.getPlayer();
        if (pos == null) {
            if (!level.isClientSide && player != null) {
                status(player, ChatFormatting.YELLOW, "message.manaessencebridge.fertilizer_not_crop");
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (fertilize((ServerLevel) level, pos) == 0) {
            if (player != null) {
                status(player, ChatFormatting.YELLOW, "message.manaessencebridge.fertilizer_ripe");
            }
            return InteractionResult.FAIL;
        }
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        if (player instanceof ServerPlayer) {
            PoolThroughput.award((ServerPlayer) player, "mana_fertilizer");
        }
        return InteractionResult.CONSUME;
    }

    /** Культура MA в этой клетке или над ней (клик по пашне); null - не культура. */
    @Nullable
    private static BlockPos target(Level level, BlockPos pos) {
        if (MysticalCrops.isCrop(level.getBlockState(pos))) {
            return pos;
        }
        return MysticalCrops.isCrop(level.getBlockState(pos.above())) ? pos.above() : null;
    }

    /** Все растущие культуры MA в квадрате вокруг center подрастают; сколько подросло. */
    int fertilize(ServerLevel level, BlockPos center) {
        int stages = full ? 16 : BridgeConfig.manaFertilizerStages();
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            if (MysticalCrops.advance(level, pos, state, stages)) {
                grown++;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                        6, 0.3, 0.2, 0.3, 0.0);
            }
        }
        if (grown > 0) {
            level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 0.6, center.getZ() + 0.5,
                    12 * radius, radius - 0.1, 0.2, radius - 0.1, 0.02);
            level.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return grown;
    }

    /** Раздатчик удобряет культуру перед собой - и обычным удобрением, и мощным. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        OptionalDispenseItemBehavior behavior = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                ServerLevel level = source.getLevel();
                BlockPos pos = target(level, source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING)));
                setSuccess(pos != null && ((ManaFertilizerItem) stack.getItem()).fertilize(level, pos) > 0);
                if (isSuccess()) {
                    stack.shrink(1);
                }
                return stack;
            }
        };
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(ModItems.MANA_FERTILIZER.get(), behavior);
            DispenserBlock.registerBehavior(ModItems.GREATER_MANA_FERTILIZER.get(), behavior);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(useKey, BridgeConfig.manaFertilizerStages())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.manaessencebridge.fertilizer_dispenser").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.manaessencebridge.fertilizer_how").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void status(Player player, ChatFormatting color, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }
}
