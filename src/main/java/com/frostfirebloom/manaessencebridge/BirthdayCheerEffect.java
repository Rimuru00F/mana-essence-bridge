package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;
import vazkii.botania.api.mana.ManaDiscountEvent;

/**
 * «Праздничное настроение» - эффект, который даёт только Праздничный
 * мана-тортик. Пока он действует:
 * - инструменты и предметы Botania тратят на 50% меньше маны (через
 *   ManaDiscountEvent из API Botania; со скидкой брони вместе не больше 90%);
 * - раз в секунду в мана-предметы игрока - кольца, планшеты, в инвентаре и в
 *   слотах Curios - приходит 500 маны в подарок (за 10 минут до 300 000).
 *   Botania-шный dispatchMana для этого не годится: он передаёт ману только
 *   от одного мана-предмета другому, поэтому раскладываем сами через API.
 */
public class BirthdayCheerEffect extends Effect {

    private static final float DISCOUNT = 0.5F;
    private static final float MAX_DISCOUNT = 0.9F;
    /** Подарочная мана в секунду, пока действует эффект. */
    private static final int GIFT_PER_SECOND = 500;

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

    /** Подарок срабатывает раз в секунду. */
    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void performEffect(net.minecraft.entity.LivingEntity entity, int amplifier) {
        if (entity instanceof PlayerEntity && !entity.world.isRemote) {
            giftMana((PlayerEntity) entity);
        }
    }

    private static void giftMana(PlayerEntity player) {
        int left = GIFT_PER_SECOND;
        vazkii.botania.api.mana.ManaItemHandler handler = vazkii.botania.api.mana.ManaItemHandler.instance();
        for (net.minecraft.item.ItemStack stack : com.google.common.collect.Iterables.concat(
                handler.getManaItems(player), handler.getManaAccesories(player))) {
            if (!(stack.getItem() instanceof vazkii.botania.api.mana.IManaItem)) {
                continue;
            }
            vazkii.botania.api.mana.IManaItem item = (vazkii.botania.api.mana.IManaItem) stack.getItem();
            int add = Math.min(left, Math.max(0, item.getMaxMana(stack) - item.getMana(stack)));
            if (add > 0) {
                item.addMana(stack, add);
                left -= add;
                if (left <= 0) {
                    return;
                }
            }
        }
    }
}
