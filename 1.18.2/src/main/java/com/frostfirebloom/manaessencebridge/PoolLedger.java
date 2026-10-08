package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import vazkii.botania.api.mana.IManaPool;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Реестр прокачанных пулов для Мана-гроссбуха: где пул, чей он, тир, мана,
 * оборот и автозабор. Хранится в данных мира (SavedData обычного мира), так
 * что гроссбух помнит и пулы в незагруженных чанках - с данными на момент,
 * когда пул в последний раз менялся или его видел игрок.
 *
 * Обновляется из ModNetwork.syncToTracking: каждое изменение пула и так
 * рассылается клиентам, заодно попадает и сюда.
 */
public class PoolLedger extends SavedData {

    private static final String NAME = ManaEssenceBridge.MODID + "_pools";

    public static final class Entry {
        public String dim = "";
        public long pos;
        public UUID owner;
        public int tier;
        public int mana;
        public int maxMana;
        public long processed;
        public boolean pull;
        /** Связи пула в сети пулов (позиции в том же измерении). */
        public long[] links = new long[0];
        /** Данные свежие: пул сейчас загружен и прочитан только что. */
        public transient boolean live;
        /** Скорость маны в минуту по двум последним замерам (hasRate - замеров уже два). */
        public transient boolean hasRate;
        public transient int rate;
        transient int sampleMana;
        transient long sampleTime = -1;
        /** Последний конденсатор, бравший ману из этого пула: тир фильтра, резерв и когда. */
        public transient int condTier;
        public transient int condReserve;
        public transient long condTime = Long.MIN_VALUE / 2;

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Замер маны раз в 30 секунд - из двух замеров считается скорость. */
    private static final int SAMPLE_TICKS = 600;
    /** Конденсатор показываем, если он брал ману в последнюю минуту. */
    public static final int CONDENSER_FRESH = 1200;

    private static PoolLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PoolLedger::load, PoolLedger::new, NAME);
    }

    private static String dimOf(Level level) {
        return level.dimension().location().toString();
    }

    private static String key(String dim, BlockPos pos) {
        return dim + "|" + pos.asLong();
    }

    public static void update(Level level, BlockPos pos, InferiumCatalystCapability cap) {
        update(level, pos, cap, level.getBlockEntity(pos));
    }

    /** Записать текущее состояние пула; пул без прокачки или без хозяина из реестра уходит. */
    public static void update(Level level, BlockPos pos, InferiumCatalystCapability cap, @Nullable BlockEntity te) {
        MinecraftServer server = level.getServer();
        if (level.isClientSide || server == null) {
            return;
        }
        PoolLedger ledger = get(server);
        String dim = dimOf(level);
        String key = key(dim, pos);
        if (!cap.isUpgraded() || cap.getOwner() == null || !(te instanceof IManaPool)) {
            if (ledger.entries.remove(key) != null) {
                ledger.setDirty();
            }
            return;
        }
        Entry e = ledger.entries.computeIfAbsent(key, k -> new Entry());
        e.dim = dim;
        e.pos = pos.asLong();
        fill(e, cap, te);
        ledger.setDirty();
    }

    public static void remove(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (level.isClientSide || server == null) {
            return;
        }
        PoolLedger ledger = get(server);
        if (ledger.entries.remove(key(dimOf(level), pos)) != null) {
            ledger.setDirty();
        }
    }

    /** Раз в 30 секунд: замер маны в загруженных пулах реестра, отсюда скорость «+N/мин». */
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (now % SAMPLE_TICKS != 0) {
            return;
        }
        Map<String, ServerLevel> levels = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.put(dimOf(level), level);
        }
        for (Entry e : get(server).entries.values()) {
            ServerLevel level = levels.get(e.dim);
            BlockPos pos = e.blockPos();
            if (level == null || !level.isLoaded(pos)) {
                e.sampleTime = -1;
                e.hasRate = false;
                continue;
            }
            BlockEntity te = level.getBlockEntity(pos);
            if (!(te instanceof IManaPool)) {
                continue;
            }
            int mana = ((IManaPool) te).getCurrentMana();
            if (e.sampleTime >= 0 && now > e.sampleTime) {
                long perMinute = (long) (mana - e.sampleMana) * 1200L / (now - e.sampleTime);
                e.rate = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, perMinute));
                e.hasRate = true;
            }
            e.sampleMana = mana;
            e.sampleTime = now;
        }
    }

    /** Конденсатор взял ману из пула - отметим это в записи пула. */
    public static void noteCondenser(Level level, BlockPos pool, int filterTier, int reserve) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Entry e = get(server).entries.get(key(dimOf(level), pool));
        if (e != null) {
            e.condTier = filterTier;
            e.condReserve = reserve;
            e.condTime = level.getGameTime();
        }
    }

    private static void fill(Entry e, InferiumCatalystCapability cap, BlockEntity te) {
        e.owner = cap.getOwner();
        e.tier = cap.getTier();
        e.mana = ((IManaPool) te).getCurrentMana();
        e.maxMana = PoolCapacity.maxMana(te);
        e.processed = cap.getProcessed();
        e.pull = cap.isPullEnabled();
        e.links = cap.getLinks().stream().mapToLong(p -> p.asLong()).toArray();
    }

    /**
     * Пулы игрока: загруженные перечитываются вживую (и исчезают из реестра,
     * если пула там больше нет), остальные - как запомнились.
     */
    public static List<Entry> listFor(MinecraftServer server, UUID owner) {
        PoolLedger ledger = get(server);
        Map<String, ServerLevel> levels = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.put(dimOf(level), level);
        }
        List<Entry> result = new ArrayList<>();
        boolean dirty = false;
        for (Iterator<Entry> it = ledger.entries.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (!owner.equals(e.owner)) {
                continue;
            }
            e.live = false;
            ServerLevel level = levels.get(e.dim);
            BlockPos pos = e.blockPos();
            if (level != null && level.isLoaded(pos)) {
                BlockEntity te = level.getBlockEntity(pos);
                InferiumCatalystCapability cap = te == null ? null
                        : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
                if (!(te instanceof IManaPool) || cap == null || !cap.isUpgraded() || !owner.equals(cap.getOwner())) {
                    it.remove();
                    dirty = true;
                    continue;
                }
                fill(e, cap, te);
                e.live = true;
                dirty = true;
            }
            result.add(e);
        }
        if (dirty) {
            ledger.setDirty();
        }
        result.sort(Comparator.comparing((Entry e) -> e.dim).thenComparing(e -> -e.tier));
        return result;
    }

    // --- сохранение ---------------------------------------------------------

    private static PoolLedger load(CompoundTag tag) {
        PoolLedger ledger = new PoolLedger();
        ListTag list = tag.getList("pools", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("owner")) {
                continue;
            }
            Entry e = new Entry();
            e.dim = t.getString("dim");
            e.pos = t.getLong("pos");
            e.owner = t.getUUID("owner");
            e.tier = t.getInt("tier");
            e.mana = t.getInt("mana");
            e.maxMana = t.getInt("max");
            e.processed = t.getLong("processed");
            e.pull = t.getBoolean("pull");
            e.links = t.getLongArray("links");
            ledger.entries.put(key(e.dim, e.blockPos()), e);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", e.dim);
            t.putLong("pos", e.pos);
            t.putUUID("owner", e.owner);
            t.putInt("tier", e.tier);
            t.putInt("mana", e.mana);
            t.putInt("max", e.maxMana);
            t.putLong("processed", e.processed);
            t.putBoolean("pull", e.pull);
            if (e.links.length > 0) {
                t.putLongArray("links", e.links);
            }
            list.add(t);
        }
        tag.put("pools", list);
        return tag;
    }
}
