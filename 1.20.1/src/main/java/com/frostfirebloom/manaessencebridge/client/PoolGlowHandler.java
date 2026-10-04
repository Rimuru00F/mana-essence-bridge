package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || mc.isPaused()) {
            return;
        }
        RandomSource random = level.random;
        ClientPoolTiers.forEachTier((pos, tierLevel) -> {
            if (random.nextInt(ONE_IN) != 0) {
                return;
            }
            if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > RANGE_SQ) {
                return;
            }
            EssenceTier tier = EssenceTier.byLevel(tierLevel);
            if (tier == null) {
                return;
            }
            DustParticleOptions dust = new DustParticleOptions(
                    new org.joml.Vector3f(tier.particleRed(), tier.particleGreen(), tier.particleBlue()), 0.8F);
            level.addParticle(dust,
                    pos.getX() + 0.15 + random.nextDouble() * 0.7,
                    pos.getY() + 0.55,
                    pos.getZ() + 0.15 + random.nextDouble() * 0.7,
                    0.0, 0.02, 0.0);
        });
    }
}
