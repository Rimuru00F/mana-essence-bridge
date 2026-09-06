package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/**
 * Тир пула хранится в capability, а капабилити TileEntity не попадают
 * в пакет обновления Botania - значит клиент сам по себе про тир не знает.
 * Поэтому шлём его вручную: при установке катализатора и при загрузке чанка.
 *
 * Нужно это только для HUD - вся игровая логика остаётся на сервере.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ManaEssenceBridge.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, PoolTierPacket.class,
                PoolTierPacket::encode, PoolTierPacket::decode, PoolTierPacket::handle);
        CHANNEL.registerMessage(1, GatePacket.class,
                GatePacket::encode, GatePacket::decode, GatePacket::handle);
    }

    /** Разослать тир всем, кто видит этот чанк. */
    public static void syncToTracking(World world, BlockPos pos, int tier) {
        if (world.isRemote) {
            return;
        }
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> world.getChunkAt(pos)),
                new PoolTierPacket(pos, tier));
    }

    /** Сообщить игроку, какие гейты прогрессии у него открыты. */
    public static void sendGates(ServerPlayerEntity player, int mask) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GatePacket(mask));
    }

    /** Отправить тир одному игроку - при заходе в зону видимости чанка. */
    public static void syncToPlayer(ServerPlayerEntity player, BlockPos pos, int tier) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PoolTierPacket(pos, tier));
    }
}
