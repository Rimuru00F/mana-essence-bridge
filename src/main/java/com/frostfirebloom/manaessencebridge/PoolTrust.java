package com.frostfirebloom.manaessencebridge;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

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
public class PoolTrust extends WorldSavedData {

    private static final String NAME = ManaEssenceBridge.MODID + "_trust";

    private final Map<UUID, Set<UUID>> friends = new HashMap<>();

    public PoolTrust() {
        super(NAME);
    }

    private static PoolTrust get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getSavedData().getOrCreate(PoolTrust::new, NAME);
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
            data.markDirty();
        }
        return changed;
    }

    @Override
    public void read(CompoundNBT tag) {
        friends.clear();
        ListNBT list = tag.getList("owners", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT t = list.getCompound(i);
            if (!t.hasUniqueId("owner")) {
                continue;
            }
            Set<UUID> set = new LinkedHashSet<>();
            ListNBT fl = t.getList("friends", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < fl.size(); j++) {
                CompoundNBT f = fl.getCompound(j);
                if (f.hasUniqueId("id")) {
                    set.add(f.getUniqueId("id"));
                }
            }
            if (!set.isEmpty()) {
                friends.put(t.getUniqueId("owner"), set);
            }
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        ListNBT list = new ListNBT();
        for (Map.Entry<UUID, Set<UUID>> e : friends.entrySet()) {
            CompoundNBT t = new CompoundNBT();
            t.putUniqueId("owner", e.getKey());
            ListNBT fl = new ListNBT();
            for (UUID f : e.getValue()) {
                CompoundNBT ft = new CompoundNBT();
                ft.putUniqueId("id", f);
                fl.add(ft);
            }
            t.put("friends", fl);
            list.add(t);
        }
        tag.put("owners", list);
        return tag;
    }
}
