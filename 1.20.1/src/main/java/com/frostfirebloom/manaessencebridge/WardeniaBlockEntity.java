package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;

/**
 * Стражения: сторож фермы в квадрате 9x9. Держит пашню увлажнённой, не даёт
 * её вытоптать, выталкивает враждебных мобов со своей площади - без урона -
 * и гасит их снаряды (стрелы, трезубцы, огненные шары, зелья ведьм) и взрывы:
 * внутри зоны взрыв отменяется целиком, а взрыв рядом не трогает зону.
 * Работает с любой пашней, не только с Mystical Agriculture.
 */
public class WardeniaBlockEntity extends FunctionalFlowerBlockEntity implements FlowerStatus {

    public static final int RANGE = 4;
    /** Сколько снарядов цветок погасил с загрузки - для Jade/TOP. */
    private int blocked;
    /** Высота «купола» над площадью, в котором гасятся снаряды. */
    private static final int SHIELD_HEIGHT = 6;
    /** Зона для мобов: на столько блоков вниз и вверх от цветка. */
    private static final int ZONE_BELOW = 2;
    private static final int ZONE_ABOVE = 6;
    private static final int MAX_MANA = 1000;
    private static final int COLOR = 0x4C8A2A;

    public WardeniaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WARDENIA_BE.get(), pos, state);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        Level level = getLevel();
        if (!(level instanceof ServerLevel) || redstoneSignal > 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        LOADED.add(this);
        // снаряды гасим каждый тик: стрела пролетает несколько блоков за тик
        stopProjectiles(server);
        // каждые 2 тика: быстрые, летающие и ползущие мобы не успевают проскочить
        if (ticksExisted % 2 == 0) {
            pushMobs(server);
        }
        if (ticksExisted % 40 == 0) {
            moisten(server);
        }
    }

    private void moisten(ServerLevel level) {
        BlockPos center = getEffectivePos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RANGE, -2, -RANGE), center.offset(RANGE, 1, RANGE))) {
            if (getMana() < BridgeConfig.wardeniaMoistenCost()) {
                return;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FarmBlock && state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 2);
                addMana(-BridgeConfig.wardeniaMoistenCost());
            }
        }
    }

    /** Гасит снаряды враждебных мобов над площадью: стрелы, трезубцы, огненные шары, зелья ведьм... */
    private void stopProjectiles(ServerLevel level) {
        int cost = BridgeConfig.wardeniaProjectileCost();
        BlockPos center = getEffectivePos();
        for (net.minecraft.world.entity.projectile.Projectile shot : level.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class,
                new AABB(center).inflate(RANGE, SHIELD_HEIGHT, RANGE),
                e -> e.isAlive() && e.getOwner() instanceof Enemy)) {
            if (getMana() < cost) {
                return;
            }
            level.sendParticles(ParticleTypes.POOF, shot.getX(), shot.getY(), shot.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            shot.discard();
            addMana(-cost);
            blocked++;
        }
    }

    /**
     * Выдворяет враждебных мобов из зоны: переносит за границу квадрата в ту
     * сторону, откуда моб пришёл, на той же высоте - так вылетают и летающие
     * (фантомы, гасты), и ползущие по стенам пауки, на которых толчок не
     * действует. Если там стена - прежний толчок. Мана - за каждое выдворение.
     */
    private void pushMobs(ServerLevel level) {
        BlockPos center = getEffectivePos();
        Vec3 middle = Vec3.atCenterOf(center);
        AABB zone = new AABB(center.getX() - RANGE, center.getY() - ZONE_BELOW, center.getZ() - RANGE,
                center.getX() + RANGE + 1, center.getY() + ZONE_ABOVE + 1, center.getZ() + RANGE + 1);
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, zone, e -> e instanceof Enemy && e.isAlive())) {
            if (getMana() < BridgeConfig.wardeniaPushCost()) {
                return;
            }
            double dx = mob.getX() - middle.x;
            double dz = mob.getZ() - middle.z;
            double far = Math.max(Math.abs(dx), Math.abs(dz));
            if (far < 1.0E-3) {
                dx = 1;
                dz = 0;
                far = 1;
            }
            // точка за границей квадрата по направлению от цветка
            double scale = (RANGE + 1.5) / far;
            Vec3 target = new Vec3(middle.x + dx * scale, mob.getY(), middle.z + dz * scale);
            Vec3 shift = target.subtract(mob.position());
            level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
            if (level.noCollision(mob, mob.getBoundingBox().move(shift))) {
                mob.teleportTo(target.x, target.y, target.z);
                Vec3 away = new Vec3(dx, 0, dz).normalize().scale(0.3);
                mob.setDeltaMovement(away.x, mob.getDeltaMovement().y, away.z);
            } else {
                Vec3 away = new Vec3(dx, 0, dz).normalize().scale(0.8);
                mob.setDeltaMovement(away.x, 0.3, away.z);
            }
            mob.hurtMarked = true;
            addMana(-BridgeConfig.wardeniaPushCost());
        }
    }

    /** Есть ли рядом с этой пашней Стражения с маной и без сигнала редстоуна. */
    private static boolean guarded(LevelAccessor level, BlockPos farmland) {
        for (BlockPos pos : BlockPos.betweenClosed(farmland.offset(-RANGE, -1, -RANGE), farmland.offset(RANGE, 2, RANGE))) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WardeniaBlockEntity) {
                WardeniaBlockEntity flower = (WardeniaBlockEntity) be;
                if (flower.redstoneSignal == 0 && flower.getMana() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Пашню у Стражении нельзя вытоптать - ни игроку, ни мобу. */
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (guarded(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Загруженные стражении: взрыв ищет их здесь, а не перебором блоков. */
    private static final java.util.Set<WardeniaBlockEntity> LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    /** Зона гашения взрывов вдвое больше зоны для мобов: 17x17, от -4 до +12 по высоте. */
    private static final int BLAST_RANGE = RANGE * 2;
    private static final int BLAST_BELOW = ZONE_BELOW * 2;
    private static final int BLAST_ABOVE = ZONE_ABOVE * 2;

    /** Точка внутри зоны гашения взрывов. */
    private boolean inZone(double x, double y, double z) {
        BlockPos c = getEffectivePos();
        return Math.abs(x - (c.getX() + 0.5)) <= BLAST_RANGE + 0.5 && Math.abs(z - (c.getZ() + 0.5)) <= BLAST_RANGE + 0.5
                && y >= c.getY() - BLAST_BELOW && y <= c.getY() + BLAST_ABOVE + 1;
    }

    private boolean canGuard(Level level) {
        return !isRemoved() && getLevel() == level && redstoneSignal == 0 && getMana() >= BridgeConfig.wardeniaExplosionCost();
    }

    /** Взрыв внутри зоны отменяется целиком: ни разрушений, ни урона. */
    public static void onExplosionStart(net.minecraftforge.event.level.ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel) || LOADED.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        Vec3 at = event.getExplosion().getPosition();
        for (WardeniaBlockEntity flower : new java.util.ArrayList<>(LOADED)) {
            if (flower.canGuard(level) && flower.inZone(at.x, at.y, at.z)) {
                event.setCanceled(true);
                flower.addMana(-BridgeConfig.wardeniaExplosionCost());
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 0.8, 0.8, 0.8, 0.05);
                level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
                return;
            }
        }
    }

    /** Взрыв рядом с зоной: блоки внутри не ломаются, игроки и животные внутри не получают урона. */
    public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel) || LOADED.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        for (WardeniaBlockEntity flower : new java.util.ArrayList<>(LOADED)) {
            if (!flower.canGuard(level)) {
                continue;
            }
            boolean blocks = event.getAffectedBlocks().removeIf(p -> flower.inZone(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
            boolean entities = event.getAffectedEntities().removeIf(e -> !(e instanceof Enemy)
                    && flower.inZone(e.getX(), e.getY(), e.getZ()));
            if (blocks || entities) {
                flower.addMana(-BridgeConfig.wardeniaExplosionCost());
            }
        }
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> statusLines() {
        return java.util.Collections.singletonList(Component.translatable("status.manaessencebridge.blocked", blocked));
    }

    @Override
    public int getMaxMana() {
        return MAX_MANA;
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
