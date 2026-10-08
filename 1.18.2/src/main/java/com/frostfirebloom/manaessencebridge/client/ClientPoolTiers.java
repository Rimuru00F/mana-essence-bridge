package com.frostfirebloom.manaessencebridge.client;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский кеш состояния пулов: тир, оборот и автозабор. Заполняется
 * пакетами с сервера.
 *
 * Отдельная карта, а не capability на клиентском BlockEntity: пакет о тире
 * вполне может прийти раньше, чем клиент создаст сам BlockEntity, и тогда
 * запись в капабилити просто потерялась бы.
 */
public final class ClientPoolTiers {

    private static final Map<BlockPos, Integer> TIERS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> PROCESSED = new ConcurrentHashMap<>();
    private static final Set<BlockPos> PULLING = ConcurrentHashMap.newKeySet();

    private ClientPoolTiers() {
    }

    public static void put(BlockPos pos, int tier, long processed, boolean pullEnabled) {
        BlockPos key = pos.immutable();
        if (tier <= 0) {
            TIERS.remove(key);
            PROCESSED.remove(key);
            PULLING.remove(key);
            return;
        }
        TIERS.put(key, tier);
        PROCESSED.put(key, processed);
        if (pullEnabled) {
            PULLING.add(key);
        } else {
            PULLING.remove(key);
        }
    }

    public static int get(BlockPos pos) {
        Integer tier = TIERS.get(pos);
        return tier == null ? 0 : tier;
    }

    /** Сколько маны прошло через пул - для строки "Оборот" в HUD и Jade. */
    public static long getProcessed(BlockPos pos) {
        Long value = PROCESSED.get(pos);
        return value == null ? 0L : value;
    }

    /** Обойти все известные прокачанные пулы - для свечения. */
    public static void forEachTier(java.util.function.BiConsumer<BlockPos, Integer> action) {
        TIERS.forEach(action);
    }

    /** Включён ли на пуле автозабор сифоном. */
    public static boolean isPullEnabled(BlockPos pos) {
        return PULLING.contains(pos);
    }

    /** Вызывается при выгрузке клиентского мира - иначе тиры протекут в следующий. */
    public static void clear() {
        TIERS.clear();
        PROCESSED.clear();
        PULLING.clear();
    }
}
