package com.frostfirebloom.manaessencebridge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Кнопка в окне Зеркала эссенции. Только клиент -> сервер. Сервер ничему не
 * верит на слово: зеркало в руке, пул, измерение, приватность и мана
 * проверяются заново (EssenceMirrorItem.handleAction).
 */
public class MirrorActionPacket {

    public static final int BUY = 0;
    public static final int SELL = 1;
    public static final int SEND_ALL = 2;

    private final int hand;
    private final int action;
    private final int tier;
    /** Сколько купить или продать - выбор в окне, сервер всё равно ограничивает. */
    private final int count;

    public MirrorActionPacket(int hand, int action, int tier, int count) {
        this.hand = hand;
        this.action = action;
        this.tier = tier;
        this.count = count;
    }

    public static void encode(MirrorActionPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.hand);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.tier);
        buf.writeVarInt(p.count);
    }

    public static MirrorActionPacket decode(FriendlyByteBuf buf) {
        return new MirrorActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MirrorActionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> EssenceMirrorItem.handleAction(player, packet.hand, packet.action, packet.tier, packet.count));
        }
        ctx.setPacketHandled(true);
    }
}
