package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
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
public class BirthdayCheerEffect extends MobEffect {

    private static final float DISCOUNT = 0.5F;
    private static final float MAX_DISCOUNT = 0.9F;
    /** Подарочная мана в секунду, пока действует эффект. */
    private static final int GIFT_PER_SECOND = 500;

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

    /** Подарок срабатывает раз в секунду. */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
        if (entity instanceof Player && !entity.level().isClientSide) {
            giftMana((Player) entity);
        }
    }

    private static void giftMana(Player player) {
        int left = GIFT_PER_SECOND;
        vazkii.botania.api.mana.ManaItemHandler handler = vazkii.botania.api.mana.ManaItemHandler.instance();
        for (net.minecraft.world.item.ItemStack stack : com.google.common.collect.Iterables.concat(
                handler.getManaItems(player), handler.getManaAccesories(player))) {
            vazkii.botania.api.mana.ManaItem item = stack.getCapability(vazkii.botania.api.BotaniaForgeCapabilities.MANA_ITEM).orElse(null);
            if (item == null) {
                continue;
            }
            int add = Math.min(left, Math.max(0, item.getMaxMana() - item.getMana()));
            if (add > 0) {
                item.addMana(add);
                left -= add;
                if (left <= 0) {
                    return;
                }
            }
        }
    }
}
