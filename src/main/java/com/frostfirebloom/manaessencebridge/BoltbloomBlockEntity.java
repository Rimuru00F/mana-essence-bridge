package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntityGeneratingFlower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Молниецвет: ловец молний. Удар молнии в радиусе 8 блоков даёт большой
 * всплеск маны (трезубец с «Громовержцем», обычная гроза). А в грозу цветок,
 * если над ним открытое небо, сам притягивает молнию - в среднем раз в
 * boltbloomSelfStrikeSeconds секунд. Эта молния декоративная: без пожара и урона.
 *
 * Дождь сам по себе маны не даёт - этим занята Райндельта из MythicBotany,
 * повторять её незачем. После удара цветок 5 секунд отдыхает, чтобы пачка
 * молний подряд не засчитывалась несколько раз.
 */
public class BoltbloomBlockEntity extends TileEntityGeneratingFlower implements FlowerStatus {

    public static final int RANGE = 8;
    private static final int VERTICAL = 16;
    private static final int COOLDOWN = 100;
    private static final int COLOR = 0x4F62A8;

    /** Загруженные молниецветы: молния ищет их здесь, а не перебором блоков. */
    private static final Set<BoltbloomBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    /** Пока цветок сам создаёт свою молнию, обработчик её пропускает - ману он уже начислил. */
    private static boolean spawningOwn;

    private long lastStrike = -COOLDOWN;

    public BoltbloomBlockEntity() {
        super(ModBlocks.BOLTBLOOM_BE.get());
    }

    /** Парящий вариант: Botania после загрузки отмечает только свои парящие блоки. */
    @Override
    public void onLoad() {
        super.onLoad();
        setFloating(FloatingManaFlowerBlock.isFloating(getBlockState()));
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        World world = getWorld();
        if (!(world instanceof ServerWorld)) {
            return;
        }
        LOADED.add(this);
        if (ticksExisted % 20 == 0) {
            trySelfStrike((ServerWorld) world);
        }
    }

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

    /** В грозу под открытым небом - изредка зовём молнию в себя. */
    private void trySelfStrike(ServerWorld world) {
        int seconds = BridgeConfig.boltbloomSelfStrikeSeconds();
        BlockPos pos = getEffectivePos();
        if (seconds <= 0 || !world.isThundering() || !underStorm(world, pos)
                || world.rand.nextInt(seconds) != 0) {
            return;
        }
        LightningBoltEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) {
            return;
        }
        bolt.moveForced(Vector3d.copyCenteredHorizontally(pos));
        bolt.setEffectOnly(true);
        spawningOwn = true;
        try {
            world.addEntity(bolt);
        } finally {
            spawningOwn = false;
        }
        struck(world);
    }

    /**
     * Под открытым ли небом цветок. Парящие Молниецветы ставят столбиком -
     * небо проверяем над верхним в столбике, иначе нижние никогда не позвали
     * бы молнию. Каждый цветок по-прежнему зовёт её с обычной частотой и
     * получает ману только за свою, так что выработка растёт линейно.
     */
    private static boolean underStorm(World world, BlockPos pos) {
        BlockPos top = pos;
        while (top.getY() < world.getHeight() - 1 && world.getTileEntity(top.up()) instanceof BoltbloomBlockEntity) {
            top = top.up();
        }
        return world.isRainingAt(top.up());
    }

    private void struck(ServerWorld world) {
        long now = world.getGameTime();
        if (now - lastStrike < COOLDOWN) {
            return;
        }
        lastStrike = now;
        int gain = Math.min(BridgeConfig.boltbloomStrikeMana(), getMaxMana() - getMana());
        if (gain <= 0) {
            return;
        }
        addMana(gain);
        markDirty();
        BlockPos pos = getEffectivePos();
        world.spawnParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                20, 0.3, 0.4, 0.3, 0.05);
    }

    /** Молния появилась в мире - её чуют все молниецветы рядом. */
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (spawningOwn || !(event.getEntity() instanceof LightningBoltEntity) || !(event.getWorld() instanceof ServerWorld)
                || LOADED.isEmpty()) {
            return;
        }
        ServerWorld world = (ServerWorld) event.getWorld();
        Vector3d at = event.getEntity().getPositionVec();
        for (BoltbloomBlockEntity flower : new ArrayList<>(LOADED)) {
            if (flower.isRemoved() || flower.getWorld() != world) {
                continue;
            }
            BlockPos pos = flower.getEffectivePos();
            double dx = at.x - (pos.getX() + 0.5);
            double dz = at.z - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= RANGE * RANGE && Math.abs(at.y - pos.getY()) <= VERTICAL) {
                flower.struck(world);
            }
        }
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.util.text.ITextComponent> statusLines() {
        if (getWorld() == null) {
            return java.util.Collections.emptyList();
        }
        long left = COOLDOWN - (getWorld().getGameTime() - lastStrike);
        if (left > 0) {
            return java.util.Collections.singletonList(new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.resting", (left + 19) / 20));
        }
        boolean storm = BridgeConfig.boltbloomSelfStrikeSeconds() > 0 && getWorld().isThundering()
                && underStorm(getWorld(), getEffectivePos());
        return java.util.Collections.singletonList(storm ? new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.storm") : new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.wait_lightning"));
    }

    @Override
    public int getMaxMana() {
        return Math.max(1000, BridgeConfig.boltbloomStrikeMana() * 2);
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
