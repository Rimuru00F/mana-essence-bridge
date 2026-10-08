package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.dispenser.OptionalDispenseBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
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
        super(new Item.Properties().group(ItemGroup.MISC));
        this.radius = radius;
        this.full = full;
        this.useKey = useKey;
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        World world = context.getWorld();
        BlockPos pos = target(world, context.getPos());
        PlayerEntity player = context.getPlayer();
        if (pos == null) {
            if (!world.isRemote && player != null) {
                status(player, TextFormatting.YELLOW, "message.manaessencebridge.fertilizer_not_crop");
            }
            return ActionResultType.FAIL;
        }
        if (world.isRemote) {
            return ActionResultType.SUCCESS;
        }
        if (fertilize((ServerWorld) world, pos) == 0) {
            if (player != null) {
                status(player, TextFormatting.YELLOW, "message.manaessencebridge.fertilizer_ripe");
            }
            return ActionResultType.FAIL;
        }
        if (player == null || !player.abilities.isCreativeMode) {
            context.getItem().shrink(1);
        }
        if (player instanceof ServerPlayerEntity) {
            PoolThroughput.award((ServerPlayerEntity) player, "mana_fertilizer");
        }
        return ActionResultType.CONSUME;
    }

    /** Культура MA в этой клетке или над ней (клик по пашне); null - не культура. */
    @Nullable
    private static BlockPos target(World world, BlockPos pos) {
        if (MysticalCrops.isCrop(world.getBlockState(pos))) {
            return pos;
        }
        return MysticalCrops.isCrop(world.getBlockState(pos.up())) ? pos.up() : null;
    }

    /** Все растущие культуры MA в квадрате вокруг center подрастают; сколько подросло. */
    int fertilize(ServerWorld world, BlockPos center) {
        int stages = full ? 16 : BridgeConfig.manaFertilizerStages();
        int grown = 0;
        for (BlockPos pos : BlockPos.getAllInBoxMutable(center.add(-radius, 0, -radius), center.add(radius, 0, radius))) {
            BlockState state = world.getBlockState(pos);
            if (MysticalCrops.advance(world, pos, state, stages)) {
                grown++;
                world.spawnParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                        6, 0.3, 0.2, 0.3, 0.0);
            }
        }
        if (grown > 0) {
            world.spawnParticle(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 0.6, center.getZ() + 0.5,
                    12 * radius, radius - 0.1, 0.2, radius - 0.1, 0.02);
            world.playSound(null, center, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0F, 1.2F);
        }
        return grown;
    }

    /** Раздатчик удобряет культуру перед собой - и обычным удобрением, и мощным. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        OptionalDispenseBehavior behavior = new OptionalDispenseBehavior() {
            @Override
            protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
                ServerWorld world = source.getWorld();
                BlockPos pos = target(world, source.getBlockPos().offset(source.getBlockState().get(DispenserBlock.FACING)));
                setSuccessful(pos != null && ((ManaFertilizerItem) stack.getItem()).fertilize(world, pos) > 0);
                if (isSuccessful()) {
                    stack.shrink(1);
                }
                return stack;
            }
        };
        event.enqueueWork(() -> {
            DispenserBlock.registerDispenseBehavior(ModItems.MANA_FERTILIZER.get(), behavior);
            DispenserBlock.registerDispenseBehavior(ModItems.GREATER_MANA_FERTILIZER.get(), behavior);
        });
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent(useKey, BridgeConfig.manaFertilizerStages())
                .mergeStyle(TextFormatting.LIGHT_PURPLE));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.fertilizer_dispenser").mergeStyle(TextFormatting.GRAY));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.fertilizer_how").mergeStyle(TextFormatting.DARK_GRAY));
    }

    private static void status(PlayerEntity player, TextFormatting color, String key) {
        player.sendStatusMessage(new TranslationTextComponent(key).mergeStyle(color), true);
    }
}
