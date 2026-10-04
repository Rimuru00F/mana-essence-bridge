package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Резонансный кристалл: снимает с пула верхний тир и возвращает
 * соответствующий катализатор.
 *
 * Не расходуется - платой служит мана из самого пула. Логика намеренно
 * такая: игрок отменяет собственное решение тем же ресурсом, который
 * в это решение вложил, и не обязан крафтить расходник ради исправления
 * ошибки. Пустой пул разобрать нельзя.
 */
public class ResonanceCrystalItem extends Item {

    public ResonanceCrystalItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(line("tooltip.manaessencebridge.crystal_use", ChatFormatting.LIGHT_PURPLE));
        tooltip.add(line("tooltip.manaessencebridge.crystal_cost", ChatFormatting.GRAY));
        tooltip.add(line("tooltip.manaessencebridge.crystal_reusable", ChatFormatting.DARK_GRAY));
    }

    private static Component line(String key, ChatFormatting color) {
        MutableComponent c = Component.translatable(key);
        c.withStyle(color);
        return c;
    }
}
