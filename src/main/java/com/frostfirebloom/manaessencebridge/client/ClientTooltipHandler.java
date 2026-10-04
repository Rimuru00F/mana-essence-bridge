package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Курс обмена прямо в подсказке самой эссенции.
 *
 * Без этого механику видно только на катализаторе и во вкладке JEI,
 * то есть игрок, у которого эссенция уже лежит в инвентаре, о ней
 * не догадается.
 */
public class ClientTooltipHandler {

    private static final String CAP_KEY = "manaessencebridge:inferium_catalyst";

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        // Пул, сохранивший прокачку: без этой строки два сломанных пула
        // разных тиров выглядят в инвентаре одинаково.
        EssenceTier stored = storedTier(event.getItemStack());
        if (stored != null) {
            event.getToolTip().add(line("tooltip.manaessencebridge.pool_tier",
                    TextFormatting.LIGHT_PURPLE, stored.getLevel(), stored.getDisplayName()));
            CompoundNBT blockTag = event.getItemStack().getChildTag("BlockEntityTag");
            if (blockTag != null && blockTag.getInt("mana") > 0) {
                event.getToolTip().add(line("tooltip.manaessencebridge.pool_mana",
                        TextFormatting.AQUA, CatalystItem.format(blockTag.getInt("mana"))));
            }
        }

        EssenceTier tier = EssenceTier.fromItem(event.getItemStack().getItem());
        if (tier == null || !tier.isEnabled()) {
            return;
        }

        event.getToolTip().add(line("tooltip.manaessencebridge.essence_sell",
                TextFormatting.AQUA, CatalystItem.format(tier.getManaPerEssence())));
        event.getToolTip().add(line("tooltip.manaessencebridge.essence_pool",
                TextFormatting.DARK_GRAY, tier.getLevel(), tier.getDisplayName()));
    }

    /**
     * Читает тир, который пул унёс с собой при поломке. Проверяем по нашему
     * ключу в BlockEntityTag, а не по id предмета: так подсказка работает
     * у любого вида пула, включая разбавленный, сказочный и аддонные.
     */
    private static EssenceTier storedTier(ItemStack stack) {
        CompoundNBT blockTag = stack.getChildTag("BlockEntityTag");
        if (blockTag == null) {
            return null;
        }
        CompoundNBT caps = blockTag.getCompound("ForgeCaps").getCompound(CAP_KEY);
        return caps.contains("tier") ? EssenceTier.byLevel(caps.getInt("tier")) : null;
    }

    private static ITextComponent line(String key, TextFormatting color, Object... args) {
        TranslationTextComponent c = new TranslationTextComponent(key, args);
        c.mergeStyle(color);
        return c;
    }
}
