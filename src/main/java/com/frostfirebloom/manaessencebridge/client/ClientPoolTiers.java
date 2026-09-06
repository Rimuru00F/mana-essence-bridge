package com.frostfirebloom.manaessencebridge.client;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский кеш "позиция пула -> тир". Заполняется пакетами с сервера.
 *
 * Отдельная карта, а не capability на клиентском TileEntity: пакет о тире
 * вполне может прийти раньше, чем клиент создаст сам TileEntity, и тогда
 * запись в капабилити просто потерялась бы.
 */
public final class ClientPoolTiers {

    private static final Map<BlockPos, Integer> TIERS = new ConcurrentHashMap<>();

    private ClientPoolTiers() {
    }

    public static void put(BlockPos pos, int tier) {
        if (tier <= 0) {
            TIERS.remove(pos.toImmutable());
        } else {
            TIERS.put(pos.toImmutable(), tier);
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
