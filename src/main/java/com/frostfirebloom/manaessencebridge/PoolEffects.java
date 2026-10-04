package com.frostfirebloom.manaessencebridge;

import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * Всплеск частиц цвета тира над пулом.
 *
 * Звук говорит "что-то произошло", а частицы - "произошло вот это",
 * поэтому цвет берём от эссенции. Вынесено отдельно, потому что этим
 * пользуются и ручное взаимодействие, и автоматическая подача.
 */
public final class PoolEffects {

    private PoolEffects() {
    }

    public static void burst(World world, BlockPos pos, EssenceTier tier, int count, double spread) {
        if (!(world instanceof ServerWorld)) {
            return;
        }
        ((ServerWorld) world).spawnParticle(
                new RedstoneParticleData(tier.particleRed(), tier.particleGreen(), tier.particleBlue(), 1.0F),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                count, spread, 0.2, spread, 0.0);
    }

    /**
     * Цепочка искр цвета тира от пула к конденсатору - видно, откуда он
     * берёт ману, даже если пул привязан жезлом за несколько блоков.
     * Не больше 12 искр, чтобы длинная привязка не забивала сеть пакетами.
     */
    public static void beam(World world, BlockPos from, BlockPos to, EssenceTier tier) {
        if (!(world instanceof ServerWorld)) {
            return;
        }
        RedstoneParticleData dust = new RedstoneParticleData(
                tier.particleRed(), tier.particleGreen(), tier.particleBlue(), 0.7F);
        double x0 = from.getX() + 0.5;
        double y0 = from.getY() + 0.6;
        double z0 = from.getZ() + 0.5;
        double dx = to.getX() + 0.5 - x0;
        double dy = to.getY() + 0.5 - y0;
        double dz = to.getZ() + 0.5 - z0;
        int steps = (int) Math.max(3, Math.min(12, Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.5));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            ((ServerWorld) world).spawnParticle(dust, x0 + dx * t, y0 + dy * t, z0 + dz * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
