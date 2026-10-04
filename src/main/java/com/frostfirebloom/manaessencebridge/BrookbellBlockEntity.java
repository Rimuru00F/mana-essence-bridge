package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntityGeneratingFlower;

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

    public BrookbellBlockEntity() {
        super(ModBlocks.BROOKBELL_BE.get());
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        World world = getWorld();
        if (!(world instanceof ServerWorld) || ticksExisted % 20 != 0) {
            return;
        }
        ServerWorld server = (ServerWorld) world;
        BlockPos center = getEffectivePos();
        int gained = 0;
        for (ItemEntity item : server.getEntitiesWithinAABB(ItemEntity.class, new AxisAlignedBB(center).grow(RANGE, 1, RANGE))) {
            if (gained + BridgeConfig.brookbellManaPerItem() > BridgeConfig.brookbellMaxPerSecond() || getMana() + gained + BridgeConfig.brookbellManaPerItem() > maxMana()) {
                break;
            }
            if (!item.isAlive() || item.getPersistentData().getBoolean(COUNTED) || !drifting(server, item)) {
                continue;
            }
            item.getPersistentData().putBoolean(COUNTED, true);
            gained += BridgeConfig.brookbellManaPerItem();
            server.spawnParticle(ParticleTypes.BUBBLE_POP,
                    item.getPosX(), item.getPosY() + 0.2, item.getPosZ(), 3, 0.1, 0.1, 0.1, 0.0);
        }
        lastGain = gained;
        if (gained > 0) {
            addMana(gained);
            markDirty();
        }
    }

    /** Предмет в текущей (не стоячей) воде и действительно плывёт. */
    private static boolean drifting(ServerWorld world, ItemEntity item) {
        FluidState fluid = world.getFluidState(item.getPosition());
        if (!fluid.isTagged(FluidTags.WATER) || fluid.isSource()) {
            return false;
        }
        Vector3d motion = item.getMotion();
        return motion.x * motion.x + motion.z * motion.z > 1.0E-4;
    }

    /** Буфер маны под настройки конфига. */
    private static int maxMana() {
        return Math.max(400, BridgeConfig.brookbellMaxPerSecond() * 20);
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.util.text.ITextComponent> statusLines() {
        return java.util.Collections.singletonList(new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.per_second", lastGain, BridgeConfig.brookbellMaxPerSecond()));
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
        return new RadiusDescriptor.Square(getEffectivePos(), RANGE);
    }
}
