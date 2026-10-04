package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Кнопка в окне Зеркала эссенции. Только клиент -> сервер. Сервер ничему не
 * верит на слово: зеркало в руке, пул, измерение, приватность и мана
 * проверяются заново (EssenceMirrorItem.handleAction).
 */
public class MirrorActionPacket {

    public static final int BUY_ONE = 0;
    public static final int BUY_STACK = 1;
    public static final int SEND_ALL = 2;

    private final int hand;
    private final int action;
    private final int tier;

    public MirrorActionPacket(int hand, int action, int tier) {
        this.hand = hand;
        this.action = action;
        this.tier = tier;
    }

    public static void encode(MirrorActionPacket p, PacketBuffer buf) {
        buf.writeVarInt(p.hand);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.tier);
    }

    public static MirrorActionPacket decode(PacketBuffer buf) {
        return new MirrorActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MirrorActionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayerEntity player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> EssenceMirrorItem.handleAction(player, packet.hand, packet.action, packet.tier));
        }
        ctx.setPacketHandled(true);
    }
}
