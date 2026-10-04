package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;

/**
 * Мистикарнация: тратит ману и ускоряет рост культур Mystical Agriculture
 * в квадрате 7x7 вокруг себя - как Агрикарнация из Botania, но для MA.
 *
 * Рост идёт через randomTick самой культуры, то есть по правилам MA.
 * Мана списывается только за реально выросшую стадию.
 *
 * Баланс: инфериум даёт с одной культуры не больше 4 эссенций (пашня шестого
 * тира), это 8000 маны при стандартном курсе. Стадий роста 7, стадия стоит
 * 5/8 цены эссенции - 8750 маны за всю культуру. Ускорять выгодно по времени,
 * но всегда в минус по мане. Плюс не больше 3 цветков на одну культуру.
 * Культуры шестого тира (Mystical Agradditions) стоят вдвое - 17 500 за культуру.
 */
public class MysticarnationBlockEntity extends FunctionalFlowerBlockEntity {

    public static final int MAX_STACK = 3;
    private static final int DELAY = 5;
    private static final int ATTEMPTS = 4;
    private static final int COLOR = 0x95A60A;

    public MysticarnationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MYSTICARNATION_BE.get(), pos, state);
    }

    /** Цена одной выросшей стадии, привязана к курсу эссенции. */
    public static int costPerStage() {
        return (int) Math.min(Integer.MAX_VALUE, (long) BridgeConfig.manaPerInferium() * 5 / 8);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        Level level = getLevel();
        if (!(level instanceof ServerLevel) || redstoneSignal > 0 || ticksExisted % DELAY != 0) {
            return;
        }
        int baseCost = costPerStage();
        if (getMana() < baseCost) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        RandomSource random = server.random;
        BlockPos center = getEffectivePos();
        for (int i = 0; i < ATTEMPTS; i++) {
            BlockPos pos = center.offset(random.nextInt(BridgeConfig.mysticarnationRange() * 2 + 1) - BridgeConfig.mysticarnationRange(), random.nextInt(3) - 1,
                    random.nextInt(BridgeConfig.mysticarnationRange() * 2 + 1) - BridgeConfig.mysticarnationRange());
            if (!server.isLoaded(pos)) {
                continue;
            }
            BlockState state = server.getBlockState(pos);
            if (!MysticalCrops.isGrowing(state)
                    || !MysticalCrops.isAmongFirstBoosters(server, pos, getBlockPos(), BridgeConfig.mysticarnationRange(), MAX_STACK)) {
                continue;
            }
            // Культуры шестого тира (Agradditions) - вдвое дороже: иначе их эссенция
            // у Эссентиды (6400 за штуку) окупала бы рост и давала бесконечную ману.
            int cost = MysticalCrops.cropTier(state) >= 6 ? baseCost * 2 : baseCost;
            if (getMana() < cost) {
                continue;
            }
            state.randomTick(server, pos, random);
            if (server.getBlockState(pos) != state) {
                addMana(-cost);
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
            }
            return; // одна попытка роста за проход
        }
    }

    @Override
    public int getMaxMana() {
        return (int) Math.min(Integer.MAX_VALUE, (long) costPerStage() * 8);
    }

    @Override
    public int getColor() {
        return COLOR;
    }

    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), BridgeConfig.mysticarnationRange());
    }
}
