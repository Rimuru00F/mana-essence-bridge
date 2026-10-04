package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;

/**
 * Пчелоцвет: пчёлы опыляют его как обычный цветок (он в теге
 * minecraft:small_flowers), и за каждое опыление цветок даёт 400 маны.
 *
 * Опыление засчитывается, когда пчела, выбравшая этот цветок, набрала нектар;
 * метка на пчеле не даёт засчитать один нектар дважды и снимается, когда
 * пчела сдаст его в улей. Чтобы рой из десятков пчёл не превратил цветок
 * в мощный генератор, после опыления он 40 секунд отдыхает: пчёлы его
 * по-прежнему опыляют, но маны не прибавится. В среднем - 10 маны в секунду.
 */
public class BumblebloomBlockEntity extends GeneratingFlowerBlockEntity implements FlowerStatus {

    private static final int COLOR = 0xF0C030;
    private static final String POLLINATED = ManaEssenceBridge.MODID + ":pollinated";
    private static final String TAG_COOLDOWN = "cooldown";

    private int cooldown;

    public BumblebloomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BUMBLEBLOOM_BE.get(), pos, state);
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        Level level = getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (ticksExisted % 5 != 0) {
            return;
        }
        BlockPos self = getBlockPos();
        for (Bee bee : level.getEntitiesOfClass(Bee.class, new AABB(self).inflate(2))) {
            CompoundTag data = bee.getPersistentData();
            if (!bee.hasNectar()) {
                data.remove(POLLINATED);
                continue;
            }
            if (data.contains(POLLINATED, Tag.TAG_BYTE) || !self.equals(bee.getSavedFlowerPos())) {
                continue;
            }
            data.putBoolean(POLLINATED, true);
            if (cooldown > 0 || getMana() + BridgeConfig.bumblebloomMana() > maxMana()) {
                continue;
            }
            addMana(BridgeConfig.bumblebloomMana());
            cooldown = BridgeConfig.bumblebloomCooldownSeconds() * 20;
            setChanged();
            ((ServerLevel) level).sendParticles(ParticleTypes.FALLING_NECTAR,
                    self.getX() + 0.5, self.getY() + 0.6, self.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.0);
        }
    }

    @Override
    public void writeToPacketNBT(CompoundTag tag) {
        super.writeToPacketNBT(tag);
        tag.putInt(TAG_COOLDOWN, cooldown);
    }

    @Override
    public void readFromPacketNBT(CompoundTag tag) {
        super.readFromPacketNBT(tag);
        cooldown = tag.getInt(TAG_COOLDOWN);
    }

    /** Буфер маны под настройки конфига. */
    private static int maxMana() {
        return Math.max(1000, BridgeConfig.bumblebloomMana() * 2);
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> statusLines() {
        return java.util.Collections.singletonList(cooldown > 0
                ? Component.translatable("status.manaessencebridge.resting", (cooldown + 19) / 20)
                : Component.translatable("status.manaessencebridge.wait_bees"));
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
        return RadiusDescriptor.Rectangle.square(getEffectivePos(), 2);
    }
}
