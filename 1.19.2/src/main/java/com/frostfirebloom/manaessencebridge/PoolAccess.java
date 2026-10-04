package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Приватные пулы (опция privatePools): чужим прокачанным пулом распоряжается
 * только его хозяин - тот, кто поставил первый катализатор. Операторы
 * сервера не ограничены. Пул без хозяина (из старых версий) открыт всем.
 */
public final class PoolAccess {

    private PoolAccess() {
    }

    /** Может ли игрок выкупать эссенцию, снимать тир, переключать сифон и привязывать конденсатор. */
    public static boolean mayUse(@Nullable Player player, @Nullable UUID owner) {
        if (!BridgeConfig.privatePools() || owner == null || player == null) {
            return true;
        }
        return owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** Может ли конденсатор, поставленный condenserOwner, брать ману из пула poolOwner. */
    public static boolean condenserMayUse(@Nullable UUID condenserOwner, @Nullable UUID poolOwner) {
        if (!BridgeConfig.privatePools() || poolOwner == null) {
            return true;
        }
        return poolOwner.equals(condenserOwner);
    }
}
