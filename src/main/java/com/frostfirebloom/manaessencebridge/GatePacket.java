package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.ClientGates;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Маска открытых гейтов прогрессии. Только сервер -> клиент. */
public class GatePacket {

    private final int mask;

    public GatePacket(int mask) {
        this.mask = mask;
    }

    public int getMask() {
        return mask;
    }

    public static void encode(GatePacket packet, PacketBuffer buffer) {
        buffer.writeVarInt(packet.mask);
    }

    public static GatePacket decode(PacketBuffer buffer) {
        return new GatePacket(buffer.readVarInt());
    }

    public static void handle(GatePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientGates.set(packet.getMask())));
        ctx.setPacketHandled(true);
    }
}
