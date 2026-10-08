package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.PlayerEntity;

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
    public static boolean mayUse(@Nullable PlayerEntity player, @Nullable UUID owner) {
        if (!BridgeConfig.privatePools() || owner == null || player == null) {
            return true;
        }
        return owner.equals(player.getUniqueID()) || player.hasPermissionLevel(2)
                || PoolTrust.trusts(player.getServer(), owner, player.getUniqueID());
    }

    /** Может ли конденсатор, поставленный condenserOwner, брать ману из пула poolOwner. */
    public static boolean condenserMayUse(@Nullable UUID condenserOwner, @Nullable UUID poolOwner) {
        if (!BridgeConfig.privatePools() || poolOwner == null) {
            return true;
        }
        return poolOwner.equals(condenserOwner)
                || PoolTrust.trusts(net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer(), poolOwner, condenserOwner);
    }
}
