package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
import vazkii.botania.api.mana.ManaDiscountEvent;

/**
 * «Праздничное настроение» - эффект, который даёт только Праздничный
 * мана-тортик. Пока он действует, инструменты и предметы Botania тратят
 * на 50% меньше маны (через ManaDiscountEvent из API Botania). Складывается
 * со скидкой брони, но вместе не больше 90%.
 */
public class BirthdayCheerEffect extends MobEffect {

    private static final float DISCOUNT = 0.5F;
    private static final float MAX_DISCOUNT = 0.9F;

    public BirthdayCheerEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF29AE0);
    }

    public static void onManaDiscount(ManaDiscountEvent event) {
        Player player = event.getEntityPlayer();
        if (player != null && player.hasEffect(ModEffects.BIRTHDAY_CHEER.get())) {
            float discount = event.getDiscount();
            event.setDiscount(Math.max(discount, Math.min(discount + DISCOUNT, MAX_DISCOUNT)));
        }
    }
}
