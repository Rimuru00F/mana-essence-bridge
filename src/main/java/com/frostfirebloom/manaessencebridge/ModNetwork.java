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

    // Поднимается при любом изменении формата пакетов, чтобы Forge честно
    // отказал во входе, а не клиент вылетел посреди игры на чужом пакете.
    // "2" - в PoolTierPacket добавился оборот пула (версия 3),
    // "3" - флаг автозабора (версия 3.1).
    // "4" - окна Мана-гроссбуха и Зеркала эссенции (версия 4.0).
    private static final String PROTOCOL_VERSION = "4";

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
        CHANNEL.registerMessage(2, LedgerPacket.class,
                LedgerPacket::encode, LedgerPacket::decode, LedgerPacket::handle);
        CHANNEL.registerMessage(3, MirrorPacket.class,
                MirrorPacket::encode, MirrorPacket::decode, MirrorPacket::handle);
        CHANNEL.registerMessage(4, MirrorActionPacket.class,
                MirrorActionPacket::encode, MirrorActionPacket::decode, MirrorActionPacket::handle);
    }

    /** Разослать состояние пула всем, кто видит этот чанк. */
    public static void syncToTracking(World world, BlockPos pos, InferiumCatalystCapability cap) {
        sendTracking(world, pos, new PoolTierPacket(pos, cap.getTier(), cap.getProcessed(), cap.isPullEnabled()));
        // Всё, что меняет пул, проходит здесь - заодно обновляем Мана-гроссбух.
        PoolLedger.update(world, pos, cap);
    }

    /** Пул сломан или потерял прокачку - клиенты должны его забыть. */
    public static void syncRemoved(World world, BlockPos pos) {
        sendTracking(world, pos, new PoolTierPacket(pos, 0, 0L, false));
        PoolLedger.remove(world, pos);
    }

    private static void sendTracking(World world, BlockPos pos, PoolTierPacket packet) {
        if (world.isRemote) {
            return;
        }
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> world.getChunkAt(pos)), packet);
    }

    /** Сообщить игроку, какие гейты прогрессии у него открыты. */
    public static void sendGates(ServerPlayerEntity player, int mask) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GatePacket(mask));
    }

    /** Отправить тир одному игроку - при заходе в зону видимости чанка. */
    public static void syncToPlayer(ServerPlayerEntity player, BlockPos pos, InferiumCatalystCapability cap) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PoolTierPacket(pos, cap.getTier(), cap.getProcessed(), cap.isPullEnabled()));
    }
}
