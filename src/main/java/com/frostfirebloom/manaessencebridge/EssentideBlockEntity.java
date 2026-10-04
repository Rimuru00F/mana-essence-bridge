package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntityGeneratingFlower;

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
public class EssentideBlockEntity extends TileEntityGeneratingFlower implements FlowerStatus {

    public static final int RANGE = 2;
    private static final int MAX_TIER = 6;
    private static final int MANA_PER_TICK = 10;
    private static final int COLOR = 0x55C5C1;
    private static final String TAG_COOLDOWN = "cooldown";

    private int cooldown;

    public EssentideBlockEntity() {
        super(ModBlocks.ESSENTIDE_BE.get());
    }

    /** Тир культуры ресурсной эссенции MA; 0 - Эссентида такое не ест. */
    public static int tierOf(net.minecraft.item.Item item) {
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
        World world = getWorld();
        if (!(world instanceof ServerWorld)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (ticksExisted % 10 != 0) {
            return;
        }
        ServerWorld server = (ServerWorld) world;
        BlockPos center = getEffectivePos();
        for (ItemEntity item : server.getEntitiesWithinAABB(ItemEntity.class, new AxisAlignedBB(center).grow(RANGE, 1, RANGE))) {
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
            markDirty();
            server.spawnParticle(ParticleTypes.HAPPY_VILLAGER,
                    item.getPosX(), item.getPosY() + 0.2, item.getPosZ(), 6, 0.15, 0.15, 0.15, 0.0);
            server.playSound(null, center, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.BLOCKS, 0.4F, 1.4F);
            return; // одна эссенция за раз
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

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.util.text.ITextComponent> statusLines() {
        return java.util.Collections.singletonList(cooldown > 0
                ? new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.digesting", (cooldown + 19) / 20)
                : new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.hungry"));
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
        return new RadiusDescriptor.Square(getEffectivePos(), RANGE);
    }
}
