package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.spark.ISparkAttachable;

import javax.annotation.Nonnull;

/**
 * Автоматизация: прокачанный Mana Pool отдаёт наружу IItemHandler, поэтому
 * в него можно закидывать эссенцию воронкой, трубой Create, логистикой
 * Mekanism - чем угодно, что умеет вставлять предметы.
 *
 * Botania сама на пуле никакого предметного хендлера не объявляет
 * (TilePool даже не переопределяет getCapability), так что конфликта нет.
 *
 * Наружу ничего не выдаётся: extractItem всегда пустой, иначе трубы
 * начали бы вытягивать из пула то, чего в нём нет.
 */
public class PoolItemHandler implements IItemHandler {

    private final TileEntity tile;

    public PoolItemHandler(TileEntity tile) {
        this.tile = tile;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        // Пул ничего не хранит - эссенция сразу становится маной.
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !BridgeConfig.automationEnabled()) {
            return stack;
        }

        World world = tile.getWorld();
        if (world == null || world.isRemote || !(tile instanceof IManaPool)) {
            return stack;
        }

        EssenceTier tier = EssenceTier.fromItem(stack.getItem());
        if (tier == null || !tier.isEnabled()) {
            return stack;
        }

        InferiumCatalystCapability cap =
                tile.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null || !cap.supports(tier)) {
            return stack;
        }

        int manaPer = tier.getManaPerEssence();
        int space = availableSpace(tile);
        int accepted = Math.min(stack.getCount(), space / manaPer);
        if (accepted <= 0) {
            return stack;
        }

        if (!simulate) {
            ((IManaPool) tile).receiveMana(manaPer * accepted);
            tile.markDirty();
            BlockPos pos = tile.getPos();
            BlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }

        if (accepted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack leftover = stack.copy();
        leftover.shrink(accepted);
        return leftover;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (!BridgeConfig.automationEnabled()) {
            return false;
        }
        EssenceTier tier = EssenceTier.fromItem(stack.getItem());
        if (tier == null || !tier.isEnabled()) {
            return false;
        }
        InferiumCatalystCapability cap =
                tile.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return cap != null && cap.supports(tier);
    }

    private static int availableSpace(TileEntity te) {
        if (te instanceof ISparkAttachable) {
            return Math.max(0, ((ISparkAttachable) te).getAvailableSpaceForMana());
        }
        return Integer.MAX_VALUE;
    }
}
