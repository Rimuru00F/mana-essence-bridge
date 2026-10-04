package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntityGeneratingFlower;

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
public class BumblebloomBlockEntity extends TileEntityGeneratingFlower implements FlowerStatus {

    private static final int COLOR = 0xF0C030;
    private static final String POLLINATED = ManaEssenceBridge.MODID + ":pollinated";
    private static final String TAG_COOLDOWN = "cooldown";

    private int cooldown;

    public BumblebloomBlockEntity() {
        super(ModBlocks.BUMBLEBLOOM_BE.get());
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        World world = getWorld();
        if (!(world instanceof ServerWorld)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (ticksExisted % 5 != 0) {
            return;
        }
        BlockPos self = getPos();
        for (BeeEntity bee : world.getEntitiesWithinAABB(BeeEntity.class, new AxisAlignedBB(self).grow(2))) {
            CompoundNBT data = bee.getPersistentData();
            if (!bee.hasNectar()) {
                data.remove(POLLINATED);
                continue;
            }
            if (data.contains(POLLINATED, Constants.NBT.TAG_BYTE) || !self.equals(bee.getFlowerPos())) {
                continue;
            }
            data.putBoolean(POLLINATED, true);
            if (cooldown > 0 || getMana() + BridgeConfig.bumblebloomMana() > maxMana()) {
                continue;
            }
            addMana(BridgeConfig.bumblebloomMana());
            cooldown = BridgeConfig.bumblebloomCooldownSeconds() * 20;
            markDirty();
            ((ServerWorld) world).spawnParticle(ParticleTypes.FALLING_NECTAR,
                    self.getX() + 0.5, self.getY() + 0.6, self.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.0);
        }
    }

    @Override
    public void writeToPacketNBT(CompoundNBT tag) {
        super.writeToPacketNBT(tag);
        tag.putInt(TAG_COOLDOWN, cooldown);
    }

    @Override
    public void readFromPacketNBT(CompoundNBT tag) {
        super.readFromPacketNBT(tag);
        cooldown = tag.getInt(TAG_COOLDOWN);
    }

    /** Буфер маны под настройки конфига. */
    private static int maxMana() {
        return Math.max(1000, BridgeConfig.bumblebloomMana() * 2);
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.util.text.ITextComponent> statusLines() {
        return java.util.Collections.singletonList(cooldown > 0
                ? new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.resting", (cooldown + 19) / 20)
                : new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.wait_bees"));
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
        return new RadiusDescriptor.Square(getEffectivePos(), 2);
    }
}
