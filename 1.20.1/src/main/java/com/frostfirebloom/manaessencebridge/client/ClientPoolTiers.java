package com.frostfirebloom.manaessencebridge.client;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский кеш "позиция пула -> тир". Заполняется пакетами с сервера.
 *
 * Отдельная карта, а не capability на клиентском BlockEntity: пакет о тире
 * вполне может прийти раньше, чем клиент создаст сам BlockEntity, и тогда
 * запись в капабилити просто потерялась бы.
 */
public final class ClientPoolTiers {

    private static final Map<BlockPos, Integer> TIERS = new ConcurrentHashMap<>();

    private ClientPoolTiers() {
    }

    public static void put(BlockPos pos, int tier) {
        if (tier <= 0) {
            TIERS.remove(pos.immutable());
        } else {
            TIERS.put(pos.immutable(), tier);
        }
    }

    public static int get(BlockPos pos) {
        Integer tier = TIERS.get(pos);
        return tier == null ? 0 : tier;
    }

    /** Вызывается при выходе из мира - иначе тиры протекут в следующий мир. */
    public static void clear() {
        TIERS.clear();
    }
}
