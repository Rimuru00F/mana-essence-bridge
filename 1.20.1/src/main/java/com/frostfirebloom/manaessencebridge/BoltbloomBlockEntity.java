package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Молниецвет: ловец молний. Удар молнии в радиусе 8 блоков даёт большой
 * всплеск маны (громоотвод, трезубец с «Громовержцем», обычная гроза).
 * А в грозу цветок, если над ним открытое небо, сам притягивает молнию -
 * в среднем раз в boltbloomSelfStrikeSeconds секунд. Эта молния декоративная:
 * без пожара и урона.
 *
 * Дождь сам по себе маны не даёт - этим занята Райндельта из MythicBotany,
 * повторять её незачем. После удара цветок 5 секунд отдыхает, чтобы пачка
 * молний подряд не засчитывалась несколько раз.
 */
public class BoltbloomBlockEntity extends GeneratingFlowerBlockEntity implements FlowerStatus {

    public static final int RANGE = 8;
    private static final int VERTICAL = 16;
    private static final int COOLDOWN = 100;
    private static final int COLOR = 0x4F62A8;

    /** Загруженные молниецветы: молния ищет их здесь, а не перебором блоков. */
    private static final Set<BoltbloomBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    /** Пока цветок сам создаёт свою молнию, обработчик её пропускает - ману он уже начислил. */
    private static boolean spawningOwn;

    private long lastStrike = -COOLDOWN;

    public BoltbloomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BOLTBLOOM_BE.get(), pos, state);
        // парящий вариант: Botania после загрузки отмечает только свои парящие блоки
        setFloating(FloatingManaFlowerBlock.isFloating(state));
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        Level level = getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        LOADED.add(this);
        if (ticksExisted % 20 == 0) {
            trySelfStrike((ServerLevel) level);
        }
    }

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

    /** В грозу под открытым небом - изредка зовём молнию в себя. */
    private void trySelfStrike(ServerLevel level) {
        int seconds = BridgeConfig.boltbloomSelfStrikeSeconds();
        BlockPos pos = getEffectivePos();
        if (seconds <= 0 || !level.isThundering() || !underStorm(level, pos)
                || level.random.nextInt(seconds) != 0) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(Vec3.atBottomCenterOf(pos));
        bolt.setVisualOnly(true);
        spawningOwn = true;
        try {
            level.addFreshEntity(bolt);
        } finally {
            spawningOwn = false;
        }
        struck(level);
    }

    /**
     * Под открытым ли небом цветок. Парящие Молниецветы ставят столбиком -
     * небо проверяем над верхним в столбике, иначе нижние никогда не позвали
     * бы молнию. Каждый цветок по-прежнему зовёт её с обычной частотой и
     * получает ману только за свою, так что выработка растёт линейно.
     */
    private static boolean underStorm(Level level, BlockPos pos) {
        BlockPos top = pos;
        while (top.getY() < level.getMaxBuildHeight() - 1 && level.getBlockEntity(top.above()) instanceof BoltbloomBlockEntity) {
            top = top.above();
        }
        return level.isRainingAt(top.above());
    }

    private void struck(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastStrike < COOLDOWN) {
            return;
        }
        lastStrike = now;
        int gain = Math.min(BridgeConfig.boltbloomStrikeMana(), getMaxMana() - getMana());
        if (gain <= 0) {
            return;
        }
        addMana(gain);
        setChanged();
        BlockPos pos = getEffectivePos();
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                20, 0.3, 0.4, 0.3, 0.05);
    }

    /** Молния появилась в мире - её чуют все молниецветы рядом. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (spawningOwn || !(event.getEntity() instanceof LightningBolt) || !(event.getLevel() instanceof ServerLevel)
                || LOADED.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        Vec3 at = event.getEntity().position();
        for (BoltbloomBlockEntity flower : new ArrayList<>(LOADED)) {
            if (flower.isRemoved() || flower.getLevel() != level) {
                continue;
            }
            BlockPos pos = flower.getEffectivePos();
            double dx = at.x - (pos.getX() + 0.5);
            double dz = at.z - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= RANGE * RANGE && Math.abs(at.y - pos.getY()) <= VERTICAL) {
                flower.struck(level);
            }
        }
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> statusLines() {
        if (getLevel() == null) {
            return java.util.Collections.emptyList();
        }
        long left = COOLDOWN - (getLevel().getGameTime() - lastStrike);
        if (left > 0) {
            return java.util.Collections.singletonList(Component.translatable("status.manaessencebridge.resting", (left + 19) / 20));
        }
        boolean storm = BridgeConfig.boltbloomSelfStrikeSeconds() > 0 && getLevel().isThundering()
                && underStorm(getLevel(), getEffectivePos());
        return java.util.Collections.singletonList(storm ? Component.translatable("status.manaessencebridge.storm") : Component.translatable("status.manaessencebridge.wait_lightning"));
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
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
    }
}
