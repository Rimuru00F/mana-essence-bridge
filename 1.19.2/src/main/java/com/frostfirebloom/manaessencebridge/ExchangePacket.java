package com.frostfirebloom.manaessencebridge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Записи курсов из датапака - клиенту, для подсказки на предмете. Только сервер -> клиент. */
public class ExchangePacket {

    private static final int MAX = 4096;

    private final List<PoolExchange.Entry> entries;

    public ExchangePacket(List<PoolExchange.Entry> entries) {
        this.entries = entries;
    }

    public static void encode(ExchangePacket p, FriendlyByteBuf buf) {
        int n = Math.min(p.entries.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            PoolExchange.Entry e = p.entries.get(i);
            buf.writeUtf(e.id);
            buf.writeBoolean(e.tag);
            buf.writeVarInt(e.tier);
            buf.writeVarInt(e.mana);
        }
    }

    public static ExchangePacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<PoolExchange.Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new PoolExchange.Entry(buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
        }
        return new ExchangePacket(list);
    }

    public static void handle(ExchangePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> PoolExchange.set(packet.entries));
        ctx.setPacketHandled(true);
    }
}
