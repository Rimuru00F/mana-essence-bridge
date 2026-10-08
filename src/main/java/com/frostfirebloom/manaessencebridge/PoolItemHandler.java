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
        if (world == null || world.isRemote || tile.isRemoved() || !(tile instanceof IManaPool)) {
            return stack;
        }

        // эссенция MA или предмет из курсов датапака (PoolExchange)
        PoolExchange.Price price = PoolExchange.priceOf(stack);
        InferiumCatalystCapability cap =
                tile.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (price == null || !price.fits(cap)) {
            return stack;
        }
        EssenceTier tier = price.look();

        int manaPer = price.mana;
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

            PoolThroughput.record(world, pos, cap, (long) manaPer * accepted, null, true);

            // Скромный всплеск: воронка подаёт по несколько раз в секунду,
            // и полноразмерный фейерверк превратился бы в мельтешение.
            // Звук не трогаем вовсе - вот он бы точно раздражал.
            PoolEffects.burst(world, pos, tier, Math.min(2 + accepted, 8), 0.25);
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
        if (!BridgeConfig.automationEnabled() || tile.isRemoved()) {
            return false;
        }
        PoolExchange.Price price = PoolExchange.priceOf(stack);
        InferiumCatalystCapability cap =
                tile.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return price != null && price.fits(cap);
    }

    private static int availableSpace(TileEntity te) {
        if (te instanceof ISparkAttachable) {
            return Math.max(0, ((ISparkAttachable) te).getAvailableSpaceForMana());
        }
        return Integer.MAX_VALUE;
    }
}
