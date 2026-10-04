package com.frostfirebloom.manaessencebridge;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Катализатор конкретного тира. Применяется ПКМ по Mana Pool и поднимает
 * пул на один тир вверх - строго по порядку, перепрыгнуть ступень нельзя.
 */
public class CatalystItem extends Item {

    private final EssenceTier tier;

    public CatalystItem(EssenceTier tier) {
        super(new Item.Properties()
                .group(tier != EssenceTier.INSANIUM || EssenceTier.agradditionsLoaded() ? ItemGroup.MISC : null)
                .maxStackSize(16));
        this.tier = tier;
    }

    public EssenceTier getTier() {
        return tier;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        EssenceTier previous = EssenceTier.byLevel(tier.getLevel() - 1);

        tooltip.add(line(TextFormatting.LIGHT_PURPLE, "tooltip.manaessencebridge.upgrades_to",
                tier.getLevel(), tier.getDisplayName()));
        if (PoolCapacity.multiplier(tier.getLevel()) > 1) {
            tooltip.add(line(TextFormatting.AQUA, "tooltip.manaessencebridge.capacity",
                    PoolCapacity.multiplier(tier.getLevel())));
        }

        if (previous != null) {
            tooltip.add(line(TextFormatting.GRAY, "tooltip.manaessencebridge.requires_tier",
                    previous.getLevel(), previous.getDisplayName()));
        } else {
            tooltip.add(line(TextFormatting.GRAY, "tooltip.manaessencebridge.requires_plain"));
        }

        tooltip.add(line(TextFormatting.DARK_GRAY, "tooltip.manaessencebridge.sell",
                format(tier.getManaPerEssence())));
        tooltip.add(line(TextFormatting.DARK_GRAY, "tooltip.manaessencebridge.buy",
                format(tier.getManaCost())));
    }

    private static ITextComponent line(TextFormatting color, String key, Object... args) {
        IFormattableTextComponent component = new TranslationTextComponent(key, args);
        component.mergeStyle(color);
        return component;
    }

    /** 512000 -> "512 000", чтобы большие числа читались в подсказке. */
    public static String format(int value) {
        return format((long) value);
    }

    /** Оборот пула за всю жизнь легко перерастает int - отсюда long. */
    public static String format(long value) {
        String raw = Long.toString(value);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (raw.length() - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(raw.charAt(i));
        }
        return sb.toString();
    }
}
