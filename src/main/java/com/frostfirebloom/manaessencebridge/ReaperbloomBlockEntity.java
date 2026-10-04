package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntityFunctionalFlower;

/**
 * Жнецвет: сам собирает созревшие культуры Mystical Agriculture в квадрате
 * 11x11 и пересаживает их. Одно семя из урожая уходит на посадку, остальное
 * падает рядом - его подберёт воронка или Hopperhock. Раз в секунду, не
 * больше одной культуры за раз; за сбор берёт ману.
 */
public class ReaperbloomBlockEntity extends TileEntityFunctionalFlower {

    private static final int DELAY = 20;
    private static final int COLOR = 0xC08A2E;

    public ReaperbloomBlockEntity() {
        super(ModBlocks.REAPERBLOOM_BE.get());
    }

    /** Цена одного сбора, привязана к курсу эссенции. */
    public static int costPerHarvest() {
        return (int) Math.min(Integer.MAX_VALUE, (long) BridgeConfig.manaPerInferium() * 3 / 20);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        World world = getWorld();
        if (!(world instanceof ServerWorld) || redstoneSignal > 0 || ticksExisted % DELAY != 0) {
            return;
        }
        int cost = costPerHarvest();
        if (getMana() < cost) {
            return;
        }
        ServerWorld server = (ServerWorld) world;
        BlockPos center = getEffectivePos();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -BridgeConfig.reaperbloomRange(); dx <= BridgeConfig.reaperbloomRange(); dx++) {
                for (int dz = -BridgeConfig.reaperbloomRange(); dz <= BridgeConfig.reaperbloomRange(); dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!server.isBlockPresent(pos)) {
                        continue;
                    }
                    BlockState state = server.getBlockState(pos);
                    if (!MysticalCrops.isRipe(state)) {
                        continue;
                    }
                    MysticalCrops.harvestAndReplant(server, pos, state);
                    addMana(-cost);
                    server.spawnParticle(ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.0);
                    return; // одна культура за проход
                }
            }
        }
    }

    @Override
    public int getMaxMana() {
        return (int) Math.min(Integer.MAX_VALUE, (long) costPerHarvest() * 10);
    }

    @Override
    public int getColor() {
        return COLOR;
    }

    @Override
    public RadiusDescriptor getRadius() {
        return new RadiusDescriptor.Square(getEffectivePos(), BridgeConfig.reaperbloomRange());
    }
}
