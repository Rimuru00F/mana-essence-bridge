package com.frostfirebloom.manaessencebridge;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

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
        super(new Item.Properties()
                .group(ItemGroup.MISC)
                .maxStackSize(1));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(line("tooltip.manaessencebridge.siphon_use", TextFormatting.LIGHT_PURPLE));
        tooltip.add(line("tooltip.manaessencebridge.siphon_what", TextFormatting.GRAY));
        tooltip.add(line("tooltip.manaessencebridge.siphon_redstone", TextFormatting.GRAY));
        tooltip.add(line("tooltip.manaessencebridge.crystal_reusable", TextFormatting.DARK_GRAY));
    }

    private static ITextComponent line(String key, TextFormatting color) {
        TranslationTextComponent c = new TranslationTextComponent(key);
        c.mergeStyle(color);
        return c;
    }
}
