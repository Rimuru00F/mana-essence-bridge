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
 * Сифон эссенции: ПКМ по прокачанному пулу включает или выключает автозабор
 * эссенции из соседних контейнеров.
 *
 * Отдельный предмет, а не ещё один режим кристалла: у каждого инструмента
 * мода ровно одно действие, и по предмету в руке сразу понятно, что
 * случится при клике. Не расходуется.
 */
public class EssenceSiphonItem extends Item {

    public EssenceSiphonItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(line("tooltip.manaessencebridge.siphon_use", ChatFormatting.LIGHT_PURPLE));
        tooltip.add(line("tooltip.manaessencebridge.siphon_what", ChatFormatting.GRAY));
        tooltip.add(line("tooltip.manaessencebridge.siphon_redstone", ChatFormatting.GRAY));
        tooltip.add(line("tooltip.manaessencebridge.crystal_reusable", ChatFormatting.DARK_GRAY));
    }

    private static Component line(String key, ChatFormatting color) {
        MutableComponent c = Component.translatable(key);
        c.withStyle(color);
        return c;
    }
}
