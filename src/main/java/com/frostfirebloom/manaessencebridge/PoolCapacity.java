package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IThrottledPacket;
import vazkii.botania.api.mana.spark.ISparkAttachable;

/**
 * Ёмкость пула растёт с тиром: x2 за каждую ступень выше первой, до x16
 * на пятом.
 *
 * Своего API для этого у Botania нет, но ёмкость пула - обычное поле manaCap,
 * которое Botania сама пишет в NBT пула и читает обратно, а значение по
 * умолчанию ставит, только если оно ещё не задано. Поэтому делаем ровно то,
 * что Botania делает при загрузке мира: сохраняем NBT пула, меняем в нём
 * manaCap и загружаем обратно. Никакого Mixin, никаких приватных полей.
 *
 * Если в будущей версии Botania ключ переименуют, ничего не сломается:
 * пул просто останется с обычной ёмкостью.
 */
public final class PoolCapacity {

    private static final String TAG_MANA_CAP = "manaCap";

    /** Ёмкость обычного пула Botania - на случай, если прочитать не удалось. */
    private static final int DEFAULT_CAPACITY = 1_000_000;

    private PoolCapacity() {
    }

    /** Во сколько раз тир увеличивает ёмкость. */
    public static int multiplier(int tier) {
        if (!BridgeConfig.boostCapacity() || tier <= 1) {
            return 1;
        }
        return 1 << (Math.min(tier, 6) - 1);
    }

    /** Сколько маны вместит этот пул на указанном тире - вместе с Кристаллами-расширителями. */
    public static int capacityFor(InferiumCatalystCapability cap, TileEntity te, int tier) {
        long value = (long) baseOf(cap, te) * multiplier(tier);
        if (cap.isUpgraded()) {
            value += value * BridgeConfig.expanderPercent() * crystals(te) / 100;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    /**
     * Кристаллы-расширители пула: касающиеся его и все, что касаются их, -
     * одна связная группа, но не больше expanderMaxCrystals. Чанки не грузим.
     */
    public static int crystals(TileEntity te) {
        World world = te.getWorld();
        if (world == null) {
            return 0;
        }
        int max = BridgeConfig.expanderMaxCrystals();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        seen.add(te.getPos());
        queue.add(te.getPos());
        int found = 0;
        while (!queue.isEmpty() && found < max) {
            BlockPos at = queue.poll();
            for (net.minecraft.util.Direction dir : net.minecraft.util.Direction.values()) {
                BlockPos next = at.offset(dir);
                if (found >= max || !seen.add(next) || !world.isBlockLoaded(next)
                        || world.getBlockState(next).getBlock() != ModBlocks.EXPANDER_CRYSTAL.get()) {
                    continue;
                }
                found++;
                queue.add(next);
            }
        }
        return found;
    }

    /**
     * Родная ёмкость пула, до нашей прокачки: у разбавленного она своя.
     * Запоминается при первом обращении - пулы, прокачанные в прошлых
     * версиях мода, их ёмкость никто не трогал, так что текущая и есть родная.
     */
    public static int baseOf(InferiumCatalystCapability cap, TileEntity te) {
        if (cap.getBaseCapacity() <= 0) {
            int current = maxMana(te);
            cap.setBaseCapacity(current > 0 ? current : DEFAULT_CAPACITY);
        }
        return cap.getBaseCapacity();
    }

    /**
     * Текущая ёмкость пула. В API 1.16.5 у IManaPool нет getMaxMana, зато есть
     * свободное место из ISparkAttachable: запас плюс свободное место и есть
     * ёмкость. Работает и на клиенте - Botania синхронизирует обе величины.
     */
    public static int maxMana(TileEntity te) {
        if (!(te instanceof IManaPool)) {
            return 0;
        }
        // Ёмкость - поле manaCap из NBT пула. «Запас + свободное место» верно не
        // всегда: над Mana Void Botania отдаёт место, равное всей ёмкости, и
        // полный пул выглядел бы вдвое больше.
        CompoundNBT tag = te.write(new CompoundNBT());
        if (tag.getInt(TAG_MANA_CAP) > 0) {
            return tag.getInt(TAG_MANA_CAP);
        }
        if (!(te instanceof ISparkAttachable)) {
            return 0;
        }
        long value = (long) ((IManaPool) te).getCurrentMana()
                + ((ISparkAttachable) te).getAvailableSpaceForMana();
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    /** Выставить пулу ёмкость по его тиру. */
    public static void apply(World world, BlockPos pos, TileEntity te, InferiumCatalystCapability cap) {
        if (world.isRemote || !(te instanceof IManaPool)) {
            return;
        }
        // Пул, который мы ни разу не прокачивали, не трогаем вовсе.
        if (!cap.isUpgraded() && cap.getBaseCapacity() <= 0) {
            return;
        }
        // Только что поставленный пул до первого тика ещё не знает своей
        // ёмкости (Botania проставит её на тике). Если и мы её не знаем -
        // ждём следующего раза, иначе разбавленный пул запомнился бы как обычный.
        if (maxMana(te) <= 0 && cap.getBaseCapacity() <= 0) {
            return;
        }
        int target = capacityFor(cap, te, Math.max(cap.getTier(), 1));

        CompoundNBT tag = te.write(new CompoundNBT());
        if (!tag.contains(TAG_MANA_CAP)) {
            return; // формат Botania поменялся - остаёмся с обычной ёмкостью
        }
        int current = tag.getInt(TAG_MANA_CAP);
        // Пул уменьшается (сломали кристалл, выключили boostCapacity): мана
        // сверх новой ёмкости не сгорает - ёмкость сжимается до неё и дальше,
        // по мере траты, при следующих пересчётах.
        if (target < current) {
            target = Math.max(target, Math.min(((IManaPool) te).getCurrentMana(), current));
        }
        if (current == target) {
            return;
        }
        tag.putInt(TAG_MANA_CAP, target);
        te.read(te.getBlockState(), tag);

        te.markDirty();
        BlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
        if (te instanceof IThrottledPacket) {
            ((IThrottledPacket) te).markDispatchable();
        }
    }
}
