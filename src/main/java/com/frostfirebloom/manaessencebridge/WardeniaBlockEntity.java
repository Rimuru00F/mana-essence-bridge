package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.world.BlockEvent;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntityFunctionalFlower;

/**
 * Стражения: сторож фермы в квадрате 9x9. Держит пашню увлажнённой, не даёт
 * её вытоптать, выталкивает враждебных мобов со своей площади - без урона -
 * и гасит их снаряды (стрелы, трезубцы, огненные шары, зелья ведьм) и взрывы:
 * внутри зоны взрыв отменяется целиком, а взрыв рядом не трогает зону.
 * Работает с любой пашней, не только с Mystical Agriculture.
 */
public class WardeniaBlockEntity extends TileEntityFunctionalFlower implements FlowerStatus {

    public static final int RANGE = 4;
    /** Сколько снарядов цветок погасил с загрузки - для Jade/TOP. */
    private int blocked;
    /** Высота «купола» над площадью, в котором гасятся снаряды. */
    private static final int SHIELD_HEIGHT = 6;
    /** Зона для мобов: на столько блоков вниз и вверх от цветка. */
    private static final int ZONE_BELOW = 2;
    /**
     * Парящую Стражению вешают над домом или фермой - она охраняет на
     * столько блоков вниз: мобов, снаряды, пашню (а взрывы - вдвое глубже).
     */
    private static final int FLOATING_BELOW = 16;
    private static final int ZONE_ABOVE = 6;
    private static final int MAX_MOISTURE = 7;
    private static final int MAX_MANA = 1000;
    private static final int COLOR = 0x4C8A2A;

    public WardeniaBlockEntity() {
        super(ModBlocks.WARDENIA_BE.get());
    }

    /** Парящий вариант: Botania после загрузки отмечает только свои парящие блоки. */
    @Override
    public void onLoad() {
        super.onLoad();
        setFloating(FloatingManaFlowerBlock.isFloating(getBlockState()));
    }

    /** Сколько блоков вниз охраняет цветок: парящий - глубже. */
    private int below() {
        return isFloating() ? FLOATING_BELOW : ZONE_BELOW;
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        World world = getWorld();
        if (!(world instanceof ServerWorld) || redstoneSignal > 0) {
            return;
        }
        ServerWorld server = (ServerWorld) world;
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

    private void moisten(ServerWorld world) {
        BlockPos center = getEffectivePos();
        for (BlockPos pos : BlockPos.getAllInBoxMutable(center.add(-RANGE, -below(), -RANGE), center.add(RANGE, 1, RANGE))) {
            if (getMana() < BridgeConfig.wardeniaMoistenCost()) {
                return;
            }
            if (!world.isBlockPresent(pos)) {
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof FarmlandBlock && state.get(FarmlandBlock.MOISTURE) < MAX_MOISTURE) {
                world.setBlockState(pos, state.with(FarmlandBlock.MOISTURE, MAX_MOISTURE), 2);
                addMana(-BridgeConfig.wardeniaMoistenCost());
            }
        }
    }

    /** Гасит снаряды враждебных мобов над площадью: стрелы, трезубцы, огненные шары, зелья ведьм... */
    private void stopProjectiles(ServerWorld world) {
        int cost = BridgeConfig.wardeniaProjectileCost();
        BlockPos center = getEffectivePos();
        for (net.minecraft.entity.projectile.ProjectileEntity shot : world.getEntitiesWithinAABB(
                net.minecraft.entity.projectile.ProjectileEntity.class,
                new AxisAlignedBB(center.getX() - RANGE, center.getY() - Math.max(SHIELD_HEIGHT, below()), center.getZ() - RANGE,
                        center.getX() + RANGE + 1, center.getY() + SHIELD_HEIGHT + 1, center.getZ() + RANGE + 1),
                e -> e.isAlive() && e.getShooter() instanceof IMob)) {
            if (getMana() < cost) {
                return;
            }
            world.spawnParticle(ParticleTypes.POOF, shot.getPosX(), shot.getPosY(), shot.getPosZ(), 6, 0.1, 0.1, 0.1, 0.02);
            shot.remove();
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
    private void pushMobs(ServerWorld world) {
        BlockPos center = getEffectivePos();
        Vector3d middle = Vector3d.copyCentered(center);
        AxisAlignedBB zone = new AxisAlignedBB(center.getX() - RANGE, center.getY() - below(), center.getZ() - RANGE,
                center.getX() + RANGE + 1, center.getY() + ZONE_ABOVE + 1, center.getZ() + RANGE + 1);
        for (LivingEntity mob : world.getEntitiesWithinAABB(LivingEntity.class, zone, e -> e instanceof IMob && e.isAlive())) {
            if (getMana() < BridgeConfig.wardeniaPushCost()) {
                return;
            }
            double dx = mob.getPosX() - middle.x;
            double dz = mob.getPosZ() - middle.z;
            double far = Math.max(Math.abs(dx), Math.abs(dz));
            if (far < 1.0E-3) {
                dx = 1;
                dz = 0;
                far = 1;
            }
            // точка за границей квадрата по направлению от цветка
            double reach = RANGE + 1.5;
            Vector3d target = new Vector3d(middle.x + dx * reach / far, mob.getPosY(), middle.z + dz * reach / far);
            // Соседние Стражении: выдворяем за край всей охраняемой площади -
            // иначе соседняя вернула бы моба обратно, и он болтался бы туда-сюда.
            for (int step = 0; step < 48 && guardedSpot(world, target.x, target.y, target.z); step++) {
                reach += 1;
                target = new Vector3d(middle.x + dx * reach / far, mob.getPosY(), middle.z + dz * reach / far);
            }
            Vector3d shift = target.subtract(mob.getPositionVec());
            world.spawnParticle(ParticleTypes.CLOUD, mob.getPosX(), mob.getPosY() + 0.5, mob.getPosZ(), 4, 0.2, 0.2, 0.2, 0.01);
            if (world.hasNoCollisions(mob, mob.getBoundingBox().offset(shift))) {
                mob.setPositionAndUpdate(target.x, target.y, target.z);
                Vector3d away = new Vector3d(dx, 0, dz).normalize().scale(0.3);
                mob.setMotion(away.x, mob.getMotion().y, away.z);
            } else {
                Vector3d away = new Vector3d(dx, 0, dz).normalize().scale(0.8);
                mob.setMotion(away.x, 0.3, away.z);
            }
            mob.velocityChanged = true;
            addMana(-BridgeConfig.wardeniaPushCost());
        }
    }

    /** Точка в зоне выдворения мобов: квадрат 9x9, от -below() до +6 по высоте. */
    private boolean inMobZone(double x, double y, double z) {
        BlockPos c = getEffectivePos();
        return Math.abs(x - (c.getX() + 0.5)) <= RANGE + 0.5 && Math.abs(z - (c.getZ() + 0.5)) <= RANGE + 0.5
                && y >= c.getY() - below() && y < c.getY() + ZONE_ABOVE + 1;
    }

    /** Охраняет ли эту точку какая-нибудь работающая Стражения этого мира. */
    private static boolean guardedSpot(World level, double x, double y, double z) {
        for (WardeniaBlockEntity flower : new java.util.ArrayList<>(LOADED)) {
            if (!flower.isRemoved() && flower.getWorld() == level && flower.redstoneSignal == 0
                    && flower.getMana() >= BridgeConfig.wardeniaPushCost() && flower.inMobZone(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Есть ли у этой пашни Стражения с маной и без сигнала редстоуна: в
     * квадрате 9x9, на пашне не выше блока над цветком и не ниже зоны цветка
     * (у парящей - на 16 блоков вниз). Ищем среди загруженных цветков.
     */
    private static boolean guarded(IWorld world, BlockPos farmland) {
        for (WardeniaBlockEntity flower : new java.util.ArrayList<>(LOADED)) {
            if (flower.isRemoved() || flower.getWorld() != world || flower.redstoneSignal != 0 || flower.getMana() <= 0) {
                continue;
            }
            BlockPos c = flower.getEffectivePos();
            if (Math.abs(farmland.getX() - c.getX()) <= RANGE && Math.abs(farmland.getZ() - c.getZ()) <= RANGE
                    && farmland.getY() >= c.getY() - flower.below() && farmland.getY() <= c.getY() + 1) {
                return true;
            }
        }
        return false;
    }

    /** Пашню у Стражении нельзя вытоптать - ни игроку, ни мобу. */
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (guarded(event.getWorld(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Загруженные стражении: взрыв ищет их здесь, а не перебором блоков. */
    private static final java.util.Set<WardeniaBlockEntity> LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    public void remove() {
        super.remove();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    /** Зона гашения взрывов вдвое больше зоны для мобов: 17x17, от -4 (у парящей -32) до +12 по высоте. */
    private static final int BLAST_RANGE = RANGE * 2;
    private static final int BLAST_ABOVE = ZONE_ABOVE * 2;

    /** Точка внутри зоны гашения взрывов. */
    private boolean inZone(double x, double y, double z) {
        BlockPos c = getEffectivePos();
        return Math.abs(x - (c.getX() + 0.5)) <= BLAST_RANGE + 0.5 && Math.abs(z - (c.getZ() + 0.5)) <= BLAST_RANGE + 0.5
                && y >= c.getY() - below() * 2 && y <= c.getY() + BLAST_ABOVE + 1;
    }

    private boolean canGuard(World world) {
        return !isRemoved() && getWorld() == world && redstoneSignal == 0 && getMana() >= BridgeConfig.wardeniaExplosionCost();
    }

    /** Взрыв внутри зоны отменяется целиком: ни разрушений, ни урона. */
    public static void onExplosionStart(net.minecraftforge.event.world.ExplosionEvent.Start event) {
        if (!(event.getWorld() instanceof ServerWorld) || LOADED.isEmpty()) {
            return;
        }
        ServerWorld world = (ServerWorld) event.getWorld();
        Vector3d at = event.getExplosion().getPosition();
        for (WardeniaBlockEntity flower : new java.util.ArrayList<>(LOADED)) {
            if (flower.canGuard(world) && flower.inZone(at.x, at.y, at.z)) {
                event.setCanceled(true);
                flower.addMana(-BridgeConfig.wardeniaExplosionCost());
                world.spawnParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 0.8, 0.8, 0.8, 0.05);
                world.playSound(null, at.x, at.y, at.z, net.minecraft.util.SoundEvents.BLOCK_FIRE_EXTINGUISH,
                        net.minecraft.util.SoundCategory.BLOCKS, 1.0F, 0.8F);
                return;
            }
        }
    }

    /** Взрыв рядом с зоной: блоки внутри не ломаются, игроки и животные внутри не получают урона. */
    public static void onExplosionDetonate(net.minecraftforge.event.world.ExplosionEvent.Detonate event) {
        if (!(event.getWorld() instanceof ServerWorld) || LOADED.isEmpty()) {
            return;
        }
        ServerWorld world = (ServerWorld) event.getWorld();
        for (WardeniaBlockEntity flower : new java.util.ArrayList<>(LOADED)) {
            if (!flower.canGuard(world)) {
                continue;
            }
            boolean blocks = event.getAffectedBlocks().removeIf(p -> flower.inZone(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
            boolean entities = event.getAffectedEntities().removeIf(e -> !(e instanceof IMob)
                    && flower.inZone(e.getPosX(), e.getPosY(), e.getPosZ()));
            if (blocks || entities) {
                flower.addMana(-BridgeConfig.wardeniaExplosionCost());
            }
        }
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.util.text.ITextComponent> statusLines() {
        return java.util.Collections.singletonList(new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.blocked", blocked));
    }

    @Override
    public int getMaxMana() {
        // Запас растёт под настройки - иначе дорогой щит от взрыва в конфиге
        // никогда не накопился бы и цветок молча перестал бы работать.
        int costliest = Math.max(BridgeConfig.wardeniaExplosionCost(),
                Math.max(BridgeConfig.wardeniaPushCost(), BridgeConfig.wardeniaProjectileCost()));
        return Math.max(MAX_MANA, costliest * 2);
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
