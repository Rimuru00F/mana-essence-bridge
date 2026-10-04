package com.frostfirebloom.manaessencebridge;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import vazkii.botania.api.internal.IManaBurst;
import vazkii.botania.api.mana.BurstProperties;
import vazkii.botania.api.mana.ILens;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IManaReceiver;

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
public class CondensationLensItem extends Item implements ILens {

    private static final int COLOR = 0x95A60A;
    private static final String CREDIT = ManaEssenceBridge.MODID + ":lens_credit";
    private static final Direction[] PUSH_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

    public CondensationLensItem() {
        super(new Item.Properties().maxStackSize(1).group(ItemGroup.MISC));
    }

    /** Настоящий всплеск долетел до пула: его мана идёт в эссенцию, а не в пул. */
    @Override
    public int getManaToTransfer(IManaBurst burst, ItemStack stack, IManaReceiver receiver) {
        int mana = burst.getMana();
        if (burst.isFake() || !(receiver instanceof TileEntity)) {
            return mana;
        }
        TileEntity pool = (TileEntity) receiver;
        InferiumCatalystCapability cap = upgradedPool(pool);
        if (cap == null) {
            return mana; // не прокачанный пул или MA нет - линза как обычная
        }
        unclog(pool, cap);
        condense(pool, cap, mana);
        PoolEffects.burst(pool.getWorld(), pool.getPos(), EssenceTier.INFERIUM, 2, 0.2);
        return 0;
    }

    /** Любой всплеск, в том числе пробный, упёрся в полный пул - разгружаем его. */
    @Override
    public boolean collideBurst(IManaBurst burst, RayTraceResult hit, boolean isManaBlock, boolean shouldKill, ItemStack stack) {
        if (hit instanceof BlockRayTraceResult) {
            World level = burst.entity().world;
            BlockPos pos = ((BlockRayTraceResult) hit).getPos();
            if (!level.isRemote && level.isBlockPresent(pos)) {
                TileEntity pool = level.getTileEntity(pos);
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
    private static InferiumCatalystCapability upgradedPool(TileEntity pool) {
        World level = pool.getWorld();
        if (level == null || level.isRemote || !(pool instanceof IManaPool)) {
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
    private static void unclog(TileEntity pool, InferiumCatalystCapability cap) {
        IManaPool mana = (IManaPool) pool;
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
        World level = pool.getWorld();
        level.notifyBlockUpdate(pool.getPos(), pool.getBlockState(), pool.getBlockState(), 3);
    }

    /** Добавить ману в кредит пула и выдать столько инфериума, сколько набралось. */
    private static void condense(TileEntity pool, InferiumCatalystCapability cap, long mana) {
        World level = pool.getWorld();
        Item essence = EssenceTier.INFERIUM.getEssenceItem();
        int cost = EssenceTier.INFERIUM.getManaCost();
        CompoundNBT data = pool.getTileData();
        long credit = data.getLong(CREDIT) + mana;
        BlockPos pos = pool.getPos();
        int stackSize = new ItemStack(essence).getMaxStackSize();
        long made = 0;
        while (credit >= cost) {
            int count = (int) Math.min(credit / cost, stackSize);
            credit -= (long) count * cost;
            made += count;
            give(level, pos, new ItemStack(essence, count));
        }
        data.putLong(CREDIT, credit);
        pool.markDirty();
        if (made > 0) {
            PoolThroughput.record(level, pos, cap, made * cost, null, false);
            PoolThroughput.awardOwner(level, cap, "lens_essence");
            PoolEffects.burst(level, pos, EssenceTier.INFERIUM, (int) Math.min(4 + made * 2, 16), 0.3);
        }
    }

    /** В контейнер вплотную к пулу (кроме пулов и конденсаторов), иначе - на сам пул. */
    private static void give(World level, BlockPos pool, ItemStack stack) {
        for (Direction dir : PUSH_ORDER) {
            BlockPos np = pool.offset(dir);
            if (stack.isEmpty() || !level.isBlockPresent(np)) {
                continue;
            }
            TileEntity be = level.getTileEntity(np);
            if (be == null || be instanceof IManaPool || be instanceof EssenceCondenserBlockEntity) {
                continue;
            }
            IItemHandler inv = be.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir.getOpposite()).orElse(null);
            if (inv != null) {
                stack = ItemHandlerHelper.insertItemStacked(inv, stack, false);
            }
        }
        if (!stack.isEmpty()) {
            ItemEntity item = new ItemEntity(level, pool.getX() + 0.5, pool.getY() + 0.7, pool.getZ() + 0.5, stack);
            item.setMotion(0, 0.1, 0);
            level.addEntity(item);
        }
    }

    @Override
    public void apply(ItemStack stack, BurstProperties props) {
        props.color = COLOR;
    }

    @Override
    public void updateBurst(IManaBurst burst, ItemStack stack) {
    }

    @Override
    public boolean doParticles(IManaBurst burst, ItemStack stack) {
        return true;
    }

    @Override
    public int getLensColor(ItemStack stack) {
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
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.lens_use").mergeStyle(TextFormatting.LIGHT_PURPLE));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.lens_what").mergeStyle(TextFormatting.GRAY));
    }
}
