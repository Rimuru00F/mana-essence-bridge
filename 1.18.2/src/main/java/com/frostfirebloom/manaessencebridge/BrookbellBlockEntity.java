package com.frostfirebloom.manaessencebridge;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import vazkii.botania.api.subtile.TileEntityGeneratingFlower;
import vazkii.botania.api.subtile.RadiusDescriptor;

/**
 * Ручейник: генерирующий цветок для любых ферм на воде - тростник, кактусы,
 * ламинария, мобы. Предметы не ест: даёт ману за то, что они проплывают мимо
 * в текущей воде. Каждый предмет засчитывается один раз (метка на сущности)
 * и плывёт дальше в сундук игрока.
 *
 * Защита от петли «воронка ловит - раздатчик снова бросает в поток»
 * (каждый раз это новая сущность): потолок выработки - 20 маны в секунду,
 * как у слабых генераторов Botania. Петля даёт не больше честной фермы.
 */
public class BrookbellBlockEntity extends TileEntityGeneratingFlower implements FlowerStatus {

    public static final int RANGE = 3;
    /** Сколько маны цветок дал за последнюю секунду - для Jade/TOP. */
    private int lastGain;
    private static final int COLOR = 0x4A8FD6;
    private static final String COUNTED = ManaEssenceBridge.MODID + ":brookbell";

    public BrookbellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BROOKBELL_BE.get(), pos, state);
        // парящий вариант: Botania после загрузки отмечает только свои парящие блоки
        setFloating(FloatingManaFlowerBlock.isFloating(state));
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        Level level = getLevel();
        if (!(level instanceof ServerLevel) || ticksExisted % 20 != 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        BlockPos center = getEffectivePos();
        int gained = 0;
        for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(RANGE, 1, RANGE))) {
            if (gained + BridgeConfig.brookbellManaPerItem() > BridgeConfig.brookbellMaxPerSecond() || getMana() + gained + BridgeConfig.brookbellManaPerItem() > maxMana()) {
                break;
            }
            if (!item.isAlive() || item.getPersistentData().getBoolean(COUNTED) || !drifting(server, item)) {
                continue;
            }
            item.getPersistentData().putBoolean(COUNTED, true);
            gained += BridgeConfig.brookbellManaPerItem();
            server.sendParticles(ParticleTypes.BUBBLE_POP,
                    item.getX(), item.getY() + 0.2, item.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
        }
        lastGain = gained;
        if (gained > 0) {
            addMana(gained);
            setChanged();
        }
    }

    /** Предмет в текущей (не стоячей) воде и действительно плывёт. */
    private static boolean drifting(ServerLevel level, ItemEntity item) {
        FluidState fluid = level.getFluidState(item.blockPosition());
        if (!fluid.is(FluidTags.WATER) || fluid.isSource()) {
            return false;
        }
        Vec3 motion = item.getDeltaMovement();
        return motion.x * motion.x + motion.z * motion.z > 1.0E-4;
    }

    /** Буфер маны под настройки конфига. */
    private static int maxMana() {
        return Math.max(400, BridgeConfig.brookbellMaxPerSecond() * 20);
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> statusLines() {
        return java.util.Collections.singletonList(new TranslatableComponent("status.manaessencebridge.per_second", lastGain, BridgeConfig.brookbellMaxPerSecond()));
    }

    @Override
    public int getMaxMana() {
        return maxMana();
    }

    @Override
    public int getColor() {
        return COLOR;
    }

    @Override
    public RadiusDescriptor getRadius() {
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }
}
