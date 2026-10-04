package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import vazkii.botania.api.mana.ManaPool;

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
    public static int capacityFor(InferiumCatalystCapability cap, BlockEntity te, int tier) {
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
    public static int crystals(BlockEntity te) {
        Level level = te.getLevel();
        if (level == null) {
            return 0;
        }
        int max = BridgeConfig.expanderMaxCrystals();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        seen.add(te.getBlockPos());
        queue.add(te.getBlockPos());
        int found = 0;
        while (!queue.isEmpty() && found < max) {
            BlockPos at = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = at.relative(dir);
                if (found >= max || !seen.add(next) || !level.isLoaded(next)
                        || !level.getBlockState(next).is(ModBlocks.EXPANDER_CRYSTAL.get())) {
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
    public static int baseOf(InferiumCatalystCapability cap, BlockEntity te) {
        if (cap.getBaseCapacity() <= 0) {
            int current = maxMana(te);
            cap.setBaseCapacity(current > 0 ? current : DEFAULT_CAPACITY);
        }
        return cap.getBaseCapacity();
    }

    /** Текущая ёмкость пула. Работает и на клиенте - Botania синхронизирует её сама. */
    public static int maxMana(BlockEntity te) {
        return te instanceof ManaPool ? ((ManaPool) te).getMaxMana() : 0;
    }

    /** Выставить пулу ёмкость по его тиру. */
    public static void apply(Level level, BlockPos pos, BlockEntity te, InferiumCatalystCapability cap) {
        if (level.isClientSide || !(te instanceof ManaPool)) {
            return;
        }
        // Пул, который мы ни разу не прокачивали, не трогаем вовсе.
        if (!cap.isUpgraded() && cap.getBaseCapacity() <= 0) {
            return;
        }
        int current = maxMana(te);
        // Только что поставленный пул до первого тика ещё не знает своей
        // ёмкости (Botania проставит её на тике). Если и мы её не знаем -
        // ждём следующего раза, иначе разбавленный пул запомнился бы как обычный.
        if (current <= 0 && cap.getBaseCapacity() <= 0) {
            return;
        }
        int target = capacityFor(cap, te, Math.max(cap.getTier(), 1));
        // Пул уменьшается (сломали кристалл, выключили boostCapacity): мана
        // сверх новой ёмкости не сгорает - ёмкость сжимается до неё и дальше,
        // по мере траты, при следующих пересчётах.
        if (target < current) {
            target = Math.max(target, Math.min(((ManaPool) te).getCurrentMana(), current));
        }
        if (current == target) {
            return;
        }

        CompoundTag tag = te.saveWithoutMetadata();
        if (!tag.contains(TAG_MANA_CAP)) {
            return; // формат Botania поменялся - остаёмся с обычной ёмкостью
        }
        tag.putInt(TAG_MANA_CAP, target);
        te.load(tag);

        te.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }
}
