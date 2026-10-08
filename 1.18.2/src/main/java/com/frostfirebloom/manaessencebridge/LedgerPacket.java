package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.LedgerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Список своих прокачанных пулов для окна Мана-гроссбуха. Только сервер -> клиент. */
public class LedgerPacket {

    /** Не больше стольких пулов в одном пакете - с запасом на любую базу. */
    public static final int MAX_ROWS = 256;

    public static final class Row {
        public final String dim;
        public final BlockPos pos;
        public final int tier;
        public final int mana;
        public final int maxMana;
        public final long processed;
        public final boolean pull;
        /** Данные свежие (пул загружен), а не «как в последний раз». */
        public final boolean live;
        /** Скорость маны в минуту; hasRate - известна ли она. */
        public boolean hasRate;
        public int rate;
        /** Конденсатор, бравший ману в последнюю минуту: тир фильтра (0 - нет) и резерв. */
        public int condTier;
        public int condReserve;
        /** Связи в сети пулов. */
        public long[] links = new long[0];

        public Row(String dim, BlockPos pos, int tier, int mana, int maxMana, long processed, boolean pull, boolean live) {
            this.dim = dim;
            this.pos = pos;
            this.tier = tier;
            this.mana = mana;
            this.maxMana = maxMana;
            this.processed = processed;
            this.pull = pull;
            this.live = live;
        }
    }

    private final List<Row> rows;

    public LedgerPacket(List<Row> rows) {
        this.rows = rows;
    }

    public static LedgerPacket of(List<PoolLedger.Entry> entries, long now) {
        List<Row> rows = new ArrayList<>();
        for (PoolLedger.Entry e : entries) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            Row row = new Row(e.dim, e.blockPos(), e.tier, e.mana, e.maxMana, e.processed, e.pull, e.live);
            row.hasRate = e.live && e.hasRate;
            row.rate = e.rate;
            row.links = e.links;
            if (now - e.condTime <= PoolLedger.CONDENSER_FRESH) {
                row.condTier = e.condTier;
                row.condReserve = e.condReserve;
            }
            rows.add(row);
        }
        return new LedgerPacket(rows);
    }

    public static void encode(LedgerPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.rows.size());
        for (Row r : packet.rows) {
            buffer.writeUtf(r.dim);
            buffer.writeBlockPos(r.pos);
            buffer.writeVarInt(r.tier);
            buffer.writeVarInt(r.mana);
            buffer.writeVarInt(r.maxMana);
            buffer.writeLong(r.processed);
            buffer.writeBoolean(r.pull);
            buffer.writeBoolean(r.live);
            buffer.writeBoolean(r.hasRate);
            buffer.writeVarInt(r.rate);
            buffer.writeVarInt(r.condTier);
            buffer.writeVarInt(r.condReserve);
            int n = Math.min(r.links.length, PoolNetwork.MAX_LINKS);
            buffer.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buffer.writeLong(r.links[i]);
            }
        }
    }

    public static LedgerPacket decode(FriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), MAX_ROWS);
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Row row = new Row(buffer.readUtf(), buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readLong(), buffer.readBoolean(), buffer.readBoolean());
            row.hasRate = buffer.readBoolean();
            row.rate = buffer.readVarInt();
            row.condTier = buffer.readVarInt();
            row.condReserve = buffer.readVarInt();
            row.links = new long[Math.min(buffer.readVarInt(), PoolNetwork.MAX_LINKS)];
            for (int j = 0; j < row.links.length; j++) {
                row.links[j] = buffer.readLong();
            }
            rows.add(row);
        }
        return new LedgerPacket(rows);
    }

    public static void handle(LedgerPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LedgerScreen.open(packet.rows)));
        ctx.setPacketHandled(true);
    }
}
