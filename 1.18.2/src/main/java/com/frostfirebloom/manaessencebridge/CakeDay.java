package com.frostfirebloom.manaessencebridge;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer)
                || event.player.tickCount % 200 != 0 || !isToday()) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.player;
        CompoundTag root = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int year = LocalDate.now().getYear();
        if (root.getInt(TAG) == year) {
            return;
        }
        root.putInt(TAG, year);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);

        ItemStack cake = new ItemStack(ModItems.BIRTHDAY_CAKE.get());
        if (!player.getInventory().add(cake)) {
            player.drop(cake, false);
        }
        player.sendMessage(new TranslatableComponent("message.manaessencebridge.cake_day")
                .withStyle(ChatFormatting.LIGHT_PURPLE), net.minecraft.Util.NIL_UUID);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        PoolThroughput.award(player, "cake_not_lie");
    }
}
