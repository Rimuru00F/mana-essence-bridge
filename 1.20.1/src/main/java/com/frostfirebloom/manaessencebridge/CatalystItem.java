package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Катализатор конкретного тира. Применяется ПКМ по Mana Pool и поднимает
 * пул на один тир вверх - строго по порядку, перепрыгнуть ступень нельзя.
 */
public class CatalystItem extends Item {

    private final EssenceTier tier;

    public CatalystItem(EssenceTier tier) {
        super(new Item.Properties().stacksTo(16));
        this.tier = tier;
    }

    public EssenceTier getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
        EssenceTier previous = EssenceTier.byLevel(tier.getLevel() - 1);

        tooltip.add(line(ChatFormatting.LIGHT_PURPLE, "tooltip.manaessencebridge.upgrades_to",
                tier.getLevel(), tier.getDisplayName()));

        if (previous != null) {
            tooltip.add(line(ChatFormatting.GRAY, "tooltip.manaessencebridge.requires_tier",
                    previous.getLevel(), previous.getDisplayName()));
        } else {
            tooltip.add(line(ChatFormatting.GRAY, "tooltip.manaessencebridge.requires_plain"));
        }

        tooltip.add(line(ChatFormatting.DARK_GRAY, "tooltip.manaessencebridge.sell",
                format(tier.getManaPerEssence())));
        tooltip.add(line(ChatFormatting.DARK_GRAY, "tooltip.manaessencebridge.buy",
                format(tier.getManaCost())));
    }

    private static Component line(ChatFormatting color, String key, Object... args) {
        MutableComponent component = Component.translatable(key, args);
        component.withStyle(color);
        return component;
    }

    /** 512000 -> "512 000", чтобы большие числа читались в подсказке. */
    public static String format(int value) {
        String raw = Integer.toString(value);
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
