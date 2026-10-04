package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;

/**
 * Жнецвет: сам собирает созревшие культуры Mystical Agriculture в квадрате
 * 11x11 и пересаживает их. Одно семя из урожая уходит на посадку, остальное
 * падает рядом - его подберёт воронка или Hopperhock. Раз в секунду, не
 * больше одной культуры за раз; за сбор берёт ману.
 */
public class ReaperbloomBlockEntity extends FunctionalFlowerBlockEntity {

    private static final int DELAY = 20;
    private static final int COLOR = 0xC08A2E;

    public ReaperbloomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REAPERBLOOM_BE.get(), pos, state);
    }

    /** Цена одного сбора, привязана к курсу эссенции. */
    public static int costPerHarvest() {
        return (int) Math.min(Integer.MAX_VALUE, (long) BridgeConfig.manaPerInferium() * 3 / 20);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        Level level = getLevel();
        if (!(level instanceof ServerLevel) || redstoneSignal > 0 || ticksExisted % DELAY != 0) {
            return;
        }
        int cost = costPerHarvest();
        if (getMana() < cost) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        BlockPos center = getEffectivePos();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -BridgeConfig.reaperbloomRange(); dx <= BridgeConfig.reaperbloomRange(); dx++) {
                for (int dz = -BridgeConfig.reaperbloomRange(); dz <= BridgeConfig.reaperbloomRange(); dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!server.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = server.getBlockState(pos);
                    if (!MysticalCrops.isRipe(state)) {
                        continue;
                    }
                    MysticalCrops.harvestAndReplant(server, pos, state);
                    addMana(-cost);
                    server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
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
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), BridgeConfig.reaperbloomRange());
    }
}
