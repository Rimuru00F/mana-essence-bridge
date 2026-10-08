package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;

/**
 * Эссентида: генерирующий цветок, который съедает ресурсные эссенции
 * Mystical Agriculture (железную, алмазную и т.д.), брошенные рядом или
 * проплывающие мимо, и отдаёт за них ману ближайшему распределителю.
 *
 * Цена растёт вдвое за тир культуры: 1/10 цены инфериума на первом тире,
 * 16/10 - на пятом, 32/10 - на шестом (200 ... 6400 маны при стандартном курсе;
 * шестой тир - культуры Mystical Agradditions). Петли «цветок растит, цветок
 * ест» нет: две эссенции пятого тира с урожая (6400) дешевле, чем вырастить
 * культуру Мистикарнацией (8750), а за культуры шестого тира Мистикарнация
 * берёт вдвое (17 500) - больше двух эссенций по 6400.
 *
 * После каждой эссенции цветок переваривает её: в среднем 10 маны за тик.
 */
public class EssentideBlockEntity extends GeneratingFlowerBlockEntity implements FlowerStatus {

    public static final int RANGE = 2;
    private static final int MAX_TIER = 6;
    private static final int MANA_PER_TICK = 10;
    private static final int COLOR = 0x55C5C1;
    private static final String TAG_COOLDOWN = "cooldown";

    private int cooldown;

    public EssentideBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ESSENTIDE_BE.get(), pos, state);
        // парящий вариант: Botania после загрузки отмечает только свои парящие блоки
        setFloating(FloatingManaFlowerBlock.isFloating(state));
    }

    /** Тир культуры ресурсной эссенции MA; 0 - Эссентида такое не ест. */
    public static int tierOf(net.minecraft.world.item.Item item) {
        return MysticalCrops.resourceTier(item);
    }

    /** Мана за одну ресурсную эссенцию культуры этого тира. */
    public static int manaFor(int tier) {
        int t = Math.min(Math.max(tier, 1), MAX_TIER);
        return (int) Math.min(Integer.MAX_VALUE, (long) BridgeConfig.manaPerInferium() * (1L << (t - 1)) * BridgeConfig.essentidePercent() / 100);
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
            return;
        }
        if (ticksExisted % 10 != 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        BlockPos center = getEffectivePos();
        for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(RANGE, 1, RANGE))) {
            if (!item.isAlive()) {
                continue;
            }
            ItemStack stack = item.getItem();
            int tier = MysticalCrops.resourceTier(stack.getItem());
            if (tier <= 0) {
                continue;
            }
            int mana = manaFor(tier);
            if (getMana() + mana > getMaxMana()) {
                return; // некуда девать - ждём распределитель
            }
            stack.shrink(1);
            item.setItem(stack);
            addMana(mana);
            cooldown = Math.max(1, mana / MANA_PER_TICK);
            setChanged();
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    item.getX(), item.getY() + 0.2, item.getZ(), 6, 0.15, 0.15, 0.15, 0.0);
            server.playSound(null, center, SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 0.4F, 1.4F);
            return; // одна эссенция за раз
        }
    }

    @Override
    public void writeToPacketNBT(net.minecraft.nbt.CompoundTag tag) {
        super.writeToPacketNBT(tag);
        tag.putInt(TAG_COOLDOWN, cooldown);
    }

    @Override
    public void readFromPacketNBT(net.minecraft.nbt.CompoundTag tag) {
        super.readFromPacketNBT(tag);
        cooldown = tag.getInt(TAG_COOLDOWN);
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> statusLines() {
        return java.util.Collections.singletonList(cooldown > 0
                ? Component.translatable("status.manaessencebridge.digesting", (cooldown + 19) / 20)
                : Component.translatable("status.manaessencebridge.hungry"));
    }

    @Override
    public int getMaxMana() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(2L * manaFor(MAX_TIER), 1L));
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
