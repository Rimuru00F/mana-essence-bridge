package com.frostfirebloom.manaessencebridge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Общие пулы: хозяин даёт друзьям доступ ко всем своим приватным пулам
 * (/manabridge trust add|remove|list). Хранится в данных мира. Имеет смысл
 * только при privatePools = true - иначе пулы и так открыты всем.
 */
public class PoolTrust extends SavedData {

    private static final String NAME = ManaEssenceBridge.MODID + "_trust";

    private final Map<UUID, Set<UUID>> friends = new HashMap<>();

    private static PoolTrust get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PoolTrust::load, PoolTrust::new, NAME);
    }

    /** Дал ли хозяин этому игроку доступ к своим пулам. */
    public static boolean trusts(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID player) {
        if (server == null || owner == null || player == null) {
            return false;
        }
        Set<UUID> set = get(server).friends.get(owner);
        return set != null && set.contains(player);
    }

    static Set<UUID> friendsOf(MinecraftServer server, UUID owner) {
        Set<UUID> set = get(server).friends.get(owner);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    /** true - список изменился. */
    static boolean set(MinecraftServer server, UUID owner, UUID friend, boolean trusted) {
        PoolTrust data = get(server);
        Set<UUID> set = data.friends.computeIfAbsent(owner, k -> new LinkedHashSet<>());
        boolean changed = trusted ? set.add(friend) : set.remove(friend);
        if (set.isEmpty()) {
            data.friends.remove(owner);
        }
        if (changed) {
            data.setDirty();
        }
        return changed;
    }

    private static PoolTrust load(CompoundTag tag) {
        PoolTrust data = new PoolTrust();
        ListTag list = tag.getList("owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("owner")) {
                continue;
            }
            Set<UUID> set = new LinkedHashSet<>();
            for (Tag f : t.getList("friends", Tag.TAG_INT_ARRAY)) {
                set.add(NbtUtils.loadUUID(f));
            }
            if (!set.isEmpty()) {
                data.friends.put(t.getUUID("owner"), set);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> e : friends.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", e.getKey());
            ListTag fl = new ListTag();
            for (UUID f : e.getValue()) {
                fl.add(NbtUtils.createUUID(f));
            }
            t.put("friends", fl);
            list.add(t);
        }
        tag.put("owners", list);
        return tag;
    }
}
