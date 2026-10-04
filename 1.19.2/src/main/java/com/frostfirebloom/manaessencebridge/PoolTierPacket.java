package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.ClientPoolTiers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Состояние пула для HUD: тир, оборот и включён ли автозабор. Только сервер -> клиент. */
public class PoolTierPacket {

    private final BlockPos pos;
    private final int tier;
    private final long processed;
    private final boolean pullEnabled;

    public PoolTierPacket(BlockPos pos, int tier, long processed, boolean pullEnabled) {
        this.pos = pos;
        this.tier = tier;
        this.processed = processed;
        this.pullEnabled = pullEnabled;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getTier() {
        return tier;
    }

    public long getProcessed() {
        return processed;
    }

    public boolean isPullEnabled() {
        return pullEnabled;
    }

    public static void encode(PoolTierPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.tier);
        buffer.writeLong(packet.processed);
        buffer.writeBoolean(packet.pullEnabled);
    }

    public static PoolTierPacket decode(FriendlyByteBuf buffer) {
        return new PoolTierPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readLong(), buffer.readBoolean());
    }

    public static void handle(PoolTierPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPoolTiers.put(packet.getPos(), packet.getTier(), packet.getProcessed(), packet.isPullEnabled())));
        ctx.setPacketHandled(true);
    }
}
