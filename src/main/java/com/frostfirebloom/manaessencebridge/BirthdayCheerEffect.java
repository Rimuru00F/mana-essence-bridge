package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;
import vazkii.botania.api.mana.ManaDiscountEvent;

/**
 * «Праздничное настроение» - эффект, который даёт только Праздничный
 * мана-тортик. Пока он действует, инструменты и предметы Botania тратят
 * на 50% меньше маны (через ManaDiscountEvent из API Botania). Складывается
 * со скидкой брони, но вместе не больше 90%.
 */
public class BirthdayCheerEffect extends Effect {

    private static final float DISCOUNT = 0.5F;
    private static final float MAX_DISCOUNT = 0.9F;

    public BirthdayCheerEffect() {
        super(EffectType.BENEFICIAL, 0xF29AE0);
    }

    public static void onManaDiscount(ManaDiscountEvent event) {
        PlayerEntity player = event.getEntityPlayer();
        if (player != null && player.isPotionActive(ModEffects.BIRTHDAY_CHEER.get())) {
            float discount = event.getDiscount();
            event.setDiscount(Math.max(discount, Math.min(discount + DISCOUNT, MAX_DISCOUNT)));
        }
    }
}
