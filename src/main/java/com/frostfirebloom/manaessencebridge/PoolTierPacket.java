package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.ClientPoolTiers;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** "Пул на этой позиции прокачан до тира N". Только сервер -> клиент. */
public class PoolTierPacket {

    private final BlockPos pos;
    private final int tier;

    public PoolTierPacket(BlockPos pos, int tier) {
        this.pos = pos;
        this.tier = tier;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getTier() {
        return tier;
    }

    public static void encode(PoolTierPacket packet, PacketBuffer buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.tier);
    }

    public static PoolTierPacket decode(PacketBuffer buffer) {
        return new PoolTierPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(PoolTierPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPoolTiers.put(packet.getPos(), packet.getTier())));
        ctx.setPacketHandled(true);
    }
}
