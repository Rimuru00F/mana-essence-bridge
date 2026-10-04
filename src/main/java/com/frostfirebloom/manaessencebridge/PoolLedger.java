package com.frostfirebloom.manaessencebridge;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
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
 * оборот и автозабор. Хранится в данных мира (WorldSavedData обычного мира),
 * так что гроссбух помнит и пулы в незагруженных чанках - с данными на момент,
 * когда пул в последний раз менялся или его видел игрок.
 *
 * Обновляется из ModNetwork.syncToTracking: каждое изменение пула и так
 * рассылается клиентам, заодно попадает и сюда.
 */
public class PoolLedger extends WorldSavedData {

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
            return BlockPos.fromLong(pos);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Замер маны раз в 30 секунд - из двух замеров считается скорость. */
    private static final int SAMPLE_TICKS = 600;
    /** Конденсатор показываем, если он брал ману в последнюю минуту. */
    public static final int CONDENSER_FRESH = 1200;

    public PoolLedger() {
        super(NAME);
    }

    private static PoolLedger get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getSavedData().getOrCreate(PoolLedger::new, NAME);
    }

    private static String dimOf(World world) {
        return world.getDimensionKey().getLocation().toString();
    }

    private static String key(String dim, BlockPos pos) {
        return dim + "|" + pos.toLong();
    }

    public static void update(World world, BlockPos pos, InferiumCatalystCapability cap) {
        update(world, pos, cap, world.getTileEntity(pos));
    }

    /** Записать текущее состояние пула; пул без прокачки или без хозяина из реестра уходит. */
    public static void update(World world, BlockPos pos, InferiumCatalystCapability cap, @Nullable TileEntity te) {
        MinecraftServer server = world.getServer();
        if (world.isRemote || server == null) {
            return;
        }
        PoolLedger ledger = get(server);
        String dim = dimOf(world);
        String key = key(dim, pos);
        if (!cap.isUpgraded() || cap.getOwner() == null || !(te instanceof IManaPool)) {
            if (ledger.entries.remove(key) != null) {
                ledger.markDirty();
            }
            return;
        }
        Entry e = ledger.entries.computeIfAbsent(key, k -> new Entry());
        e.dim = dim;
        e.pos = pos.toLong();
        fill(e, cap, te);
        ledger.markDirty();
    }

    public static void remove(World world, BlockPos pos) {
        MinecraftServer server = world.getServer();
        if (world.isRemote || server == null) {
            return;
        }
        PoolLedger ledger = get(server);
        if (ledger.entries.remove(key(dimOf(world), pos)) != null) {
            ledger.markDirty();
        }
    }

    /** Раз в 30 секунд: замер маны в загруженных пулах реестра, отсюда скорость «+N/мин». */
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long now = server.getWorld(World.OVERWORLD).getGameTime();
        if (now % SAMPLE_TICKS != 0) {
            return;
        }
        Map<String, ServerWorld> levels = new LinkedHashMap<>();
        for (ServerWorld level : server.getWorlds()) {
            levels.put(dimOf(level), level);
        }
        for (Entry e : get(server).entries.values()) {
            ServerWorld level = levels.get(e.dim);
            BlockPos pos = e.blockPos();
            if (level == null || !level.isBlockPresent(pos)) {
                e.sampleTime = -1;
                e.hasRate = false;
                continue;
            }
            TileEntity te = level.getTileEntity(pos);
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
    public static void noteCondenser(World level, BlockPos pool, int filterTier, int reserve) {
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

    private static void fill(Entry e, InferiumCatalystCapability cap, TileEntity te) {
        e.owner = cap.getOwner();
        e.tier = cap.getTier();
        e.mana = ((IManaPool) te).getCurrentMana();
        e.maxMana = PoolCapacity.maxMana(te);
        e.processed = cap.getProcessed();
        e.pull = cap.isPullEnabled();
    }

    /**
     * Пулы игрока: загруженные перечитываются вживую (и исчезают из реестра,
     * если пула там больше нет), остальные - как запомнились.
     */
    public static List<Entry> listFor(MinecraftServer server, UUID owner) {
        PoolLedger ledger = get(server);
        Map<String, ServerWorld> worlds = new LinkedHashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(dimOf(world), world);
        }
        List<Entry> result = new ArrayList<>();
        boolean dirty = false;
        for (Iterator<Entry> it = ledger.entries.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (!owner.equals(e.owner)) {
                continue;
            }
            e.live = false;
            ServerWorld world = worlds.get(e.dim);
            BlockPos pos = e.blockPos();
            if (world != null && world.isBlockPresent(pos)) {
                TileEntity te = world.getTileEntity(pos);
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
            ledger.markDirty();
        }
        result.sort(Comparator.comparing((Entry e) -> e.dim).thenComparing(e -> -e.tier));
        return result;
    }

    // --- сохранение ---------------------------------------------------------

    @Override
    public void read(CompoundNBT tag) {
        entries.clear();
        ListNBT list = tag.getList("pools", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT t = list.getCompound(i);
            if (!t.hasUniqueId("owner")) {
                continue;
            }
            Entry e = new Entry();
            e.dim = t.getString("dim");
            e.pos = t.getLong("pos");
            e.owner = t.getUniqueId("owner");
            e.tier = t.getInt("tier");
            e.mana = t.getInt("mana");
            e.maxMana = t.getInt("max");
            e.processed = t.getLong("processed");
            e.pull = t.getBoolean("pull");
            entries.put(key(e.dim, e.blockPos()), e);
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        ListNBT list = new ListNBT();
        for (Entry e : entries.values()) {
            CompoundNBT t = new CompoundNBT();
            t.putString("dim", e.dim);
            t.putLong("pos", e.pos);
            t.putUniqueId("owner", e.owner);
            t.putInt("tier", e.tier);
            t.putInt("mana", e.mana);
            t.putInt("max", e.maxMana);
            t.putLong("processed", e.processed);
            t.putBoolean("pull", e.pull);
            list.add(t);
        }
        tag.put("pools", list);
        return tag;
    }
}
