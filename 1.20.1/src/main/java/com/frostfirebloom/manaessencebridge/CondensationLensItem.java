package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import vazkii.botania.api.internal.ManaBurst;
import vazkii.botania.api.mana.BasicLensItem;
import vazkii.botania.api.mana.BurstProperties;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Линза конденсации для распределителя маны: всплеск, попавший в прокачанный
 * пул, не наполняет его, а сразу превращается в инфериум - по цене выкупа,
 * как у Конденсатора. Мана копится в пуле «в кредит», пока её не хватит на
 * эссенцию (обычный всплеск несёт около 160 маны, инфериум стоит 2500 -
 * примерно 16 всплесков). Крафт вверх в Mystical Agriculture бесплатный,
 * поэтому старшие тиры линзе не нужны: они только растягивали бы ожидание.
 *
 * Полный пул распределитель Botania не обстреливает вовсе. Поэтому линза
 * разгружает его сама: пробный всплеск, которым распределитель проверяет
 * путь (а он делает это при каждом своём изменении), или обычный всплеск,
 * попав в полный пул, снимает с него ману ровно на одну эссенцию - этого
 * хватает, чтобы пул перестал быть полным и распределитель снова стрелял.
 *
 * Готовая эссенция уходит в контейнер вплотную к пулу, а если его нет -
 * ложится на пул.
 */
public class CondensationLensItem extends Item implements BasicLensItem {

    private static final int COLOR = 0x95A60A;
    private static final String CREDIT = ManaEssenceBridge.MODID + ":lens_credit";
    private static final Direction[] PUSH_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

    public CondensationLensItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** Настоящий всплеск долетел до пула: его мана идёт в эссенцию, а не в пул. */
    @Override
    public int getManaToTransfer(ManaBurst burst, ItemStack stack, ManaReceiver receiver) {
        int mana = burst.getMana();
        if (burst.isFake() || !(receiver instanceof BlockEntity)) {
            return mana;
        }
        BlockEntity pool = (BlockEntity) receiver;
        InferiumCatalystCapability cap = upgradedPool(pool);
        if (cap == null) {
            return mana; // не прокачанный пул или MA нет - линза как обычная
        }
        unclog(pool, cap);
        condense(pool, cap, mana);
        PoolEffects.burst(pool.getLevel(), pool.getBlockPos(), EssenceTier.INFERIUM, 2, 0.2);
        return 0;
    }

    /** Любой всплеск, в том числе пробный, упёрся в полный пул - разгружаем его. */
    @Override
    public boolean collideBurst(ManaBurst burst, HitResult hit, boolean isManaBlock, boolean shouldKill, ItemStack stack) {
        if (hit instanceof BlockHitResult) {
            Level level = burst.entity().level();
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            if (!level.isClientSide && level.isLoaded(pos)) {
                BlockEntity pool = level.getBlockEntity(pos);
                InferiumCatalystCapability cap = pool == null ? null : upgradedPool(pool);
                if (cap != null) {
                    unclog(pool, cap);
                }
            }
        }
        return shouldKill;
    }

    /** Capability прокачанного пула на сервере, если инфериум доступен; иначе null. */
    @Nullable
    private static InferiumCatalystCapability upgradedPool(BlockEntity pool) {
        Level level = pool.getLevel();
        if (level == null || level.isClientSide || !(pool instanceof ManaPool)) {
            return null;
        }
        InferiumCatalystCapability cap = pool.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null || !cap.isUpgraded() || !EssenceTier.INFERIUM.isEnabled()) {
            return null;
        }
        Item essence = EssenceTier.INFERIUM.getEssenceItem();
        return essence == null || essence == Items.AIR ? null : cap;
    }

    /** Полный пул: снять ману на одну эссенцию, чтобы распределитель снова стрелял. */
    private static void unclog(BlockEntity pool, InferiumCatalystCapability cap) {
        ManaPool mana = (ManaPool) pool;
        int max = PoolCapacity.maxMana(pool);
        int current = mana.getCurrentMana();
        if (max <= 0 || current < max) {
            return;
        }
        int take = current - Math.max(0, max - EssenceTier.INFERIUM.getManaCost());
        if (take <= 0) {
            return;
        }
        mana.receiveMana(-take);
        condense(pool, cap, take);
        Level level = pool.getLevel();
        level.sendBlockUpdated(pool.getBlockPos(), pool.getBlockState(), pool.getBlockState(), 3);
    }

    /** Добавить ману в кредит пула и выдать столько инфериума, сколько набралось. */
    private static void condense(BlockEntity pool, InferiumCatalystCapability cap, long mana) {
        Level level = pool.getLevel();
        Item essence = EssenceTier.INFERIUM.getEssenceItem();
        int cost = EssenceTier.INFERIUM.getManaCost();
        CompoundTag data = pool.getPersistentData();
        long credit = data.getLong(CREDIT) + mana;
        BlockPos pos = pool.getBlockPos();
        int stackSize = new ItemStack(essence).getMaxStackSize();
        long made = 0;
        while (credit >= cost) {
            int count = (int) Math.min(credit / cost, stackSize);
            credit -= (long) count * cost;
            made += count;
            give(level, pos, new ItemStack(essence, count));
        }
        data.putLong(CREDIT, credit);
        pool.setChanged();
        if (made > 0) {
            PoolThroughput.record(level, pos, cap, made * cost, null, false);
            PoolThroughput.awardOwner(level, cap, "lens_essence");
            PoolEffects.burst(level, pos, EssenceTier.INFERIUM, (int) Math.min(4 + made * 2, 16), 0.3);
        }
    }

    /** В контейнер вплотную к пулу (кроме пулов и конденсаторов), иначе - на сам пул. */
    private static void give(Level level, BlockPos pool, ItemStack stack) {
        for (Direction dir : PUSH_ORDER) {
            BlockPos np = pool.relative(dir);
            if (stack.isEmpty() || !level.isLoaded(np)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(np);
            if (be == null || be instanceof ManaPool || be instanceof EssenceCondenserBlockEntity) {
                continue;
            }
            IItemHandler inv = be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).orElse(null);
            if (inv != null) {
                stack = ItemHandlerHelper.insertItemStacked(inv, stack, false);
            }
        }
        if (!stack.isEmpty()) {
            ItemEntity item = new ItemEntity(level, pool.getX() + 0.5, pool.getY() + 0.7, pool.getZ() + 0.5, stack);
            item.setDeltaMovement(0, 0.1, 0);
            level.addFreshEntity(item);
        }
    }

    @Override
    public void apply(ItemStack stack, BurstProperties props, Level level) {
        props.color = COLOR;
    }

    @Override
    public void updateBurst(ManaBurst burst, ItemStack stack) {
    }

    @Override
    public boolean doParticles(ManaBurst burst, ItemStack stack) {
        return true;
    }

    @Override
    public int getLensColor(ItemStack stack, Level level) {
        return COLOR;
    }

    @Override
    public boolean canCombineLenses(ItemStack sourceLens, ItemStack compositeLens) {
        return false;
    }

    @Override
    public ItemStack getCompositeLens(ItemStack stack) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack setCompositeLens(ItemStack sourceLens, ItemStack compositeLens) {
        return sourceLens;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.manaessencebridge.lens_use").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.manaessencebridge.lens_what").withStyle(ChatFormatting.GRAY));
    }
}
