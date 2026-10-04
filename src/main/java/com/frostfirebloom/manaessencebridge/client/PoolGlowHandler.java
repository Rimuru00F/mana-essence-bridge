package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Свечение прокачанных пулов: изредка над пулом вспыхивает искра цвета
 * его тира. Так тир видно издалека, без HUD и Jade.
 *
 * Только клиент и только пулы в радиусе 32 блоков. Позиции и тиры клиент
 * и так знает - они приходят для HUD, - так что новых пакетов не нужно.
 * Около полутора искр в секунду на пул: заметно, но не мельтешит.
 */
public class PoolGlowHandler {

    private static final double RANGE_SQ = 32.0 * 32.0;
    private static final int ONE_IN = 12;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !BridgeConfig.poolGlow()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.world;
        ClientPlayerEntity player = mc.player;
        if (world == null || player == null || mc.isGamePaused()) {
            return;
        }
        Random random = world.rand;
        ClientPoolTiers.forEachTier((pos, tierLevel) -> {
            if (random.nextInt(ONE_IN) != 0) {
                return;
            }
            if (pos.distanceSq(player.getPosX(), player.getPosY(), player.getPosZ(), true) > RANGE_SQ) {
                return;
            }
            EssenceTier tier = EssenceTier.byLevel(tierLevel);
            if (tier == null) {
                return;
            }
            RedstoneParticleData dust = new RedstoneParticleData(
                    tier.particleRed(), tier.particleGreen(), tier.particleBlue(), 0.8F);
            world.addParticle(dust,
                    pos.getX() + 0.15 + random.nextDouble() * 0.7,
                    pos.getY() + 0.55,
                    pos.getZ() + 0.15 + random.nextDouble() * 0.7,
                    0.0, 0.02, 0.0);
        });
    }
}
