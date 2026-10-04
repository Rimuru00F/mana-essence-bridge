package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.time.LocalDate;
import java.time.Month;

/**
 * Пасхалка 17 октября - день рождения автора мода: каждый игрок раз в год
 * получает Праздничный мана-тортик, сообщение и скрытое достижение
 * «Тортик - не ложь». «С днём рождения, Римуру!» - за съеденный кусок.
 * Над пустыми конденсаторами в этот день парит тортик (см. CondenserRenderer).
 *
 * Дата - по часам сервера (на клиенте - по часам игрока). Отметка о подарке
 * лежит в persistent-данных игрока, так что второй раз за год он не выпадет.
 */
public class CakeDay {

    private static final String TAG = ManaEssenceBridge.MODID + ":cake_year";

    private static long checkedMinute = -1;
    private static boolean today;

    /** Сегодня 17 октября? Дату перечитываем не чаще раза в минуту. */
    public static boolean isToday() {
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != checkedMinute) {
            checkedMinute = minute;
            LocalDate date = LocalDate.now();
            today = date.getMonth() == Month.OCTOBER && date.getDayOfMonth() == 17;
        }
        return today;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayerEntity)
                || event.player.ticksExisted % 200 != 0 || !isToday()) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        CompoundNBT root = player.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        int year = LocalDate.now().getYear();
        if (root.getInt(TAG) == year) {
            return;
        }
        root.putInt(TAG, year);
        player.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, root);

        ItemStack cake = new ItemStack(ModItems.BIRTHDAY_CAKE.get());
        if (!player.inventory.addItemStackToInventory(cake)) {
            player.dropItem(cake, false);
        }
        player.sendMessage(new TranslationTextComponent("message.manaessencebridge.cake_day")
                .mergeStyle(TextFormatting.LIGHT_PURPLE), Util.DUMMY_UUID);
        player.world.playSound(null, player.getPosition(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 0.6F, 1.2F);
        PoolThroughput.award(player, "cake_not_lie");
    }
}
