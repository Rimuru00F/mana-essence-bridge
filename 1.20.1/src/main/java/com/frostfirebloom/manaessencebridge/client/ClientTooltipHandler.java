package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
                    ChatFormatting.LIGHT_PURPLE, stored.getLevel(), stored.getDisplayName()));
            CompoundTag blockTag = event.getItemStack().getTagElement("BlockEntityTag");
            if (blockTag != null && blockTag.getInt("mana") > 0) {
                event.getToolTip().add(line("tooltip.manaessencebridge.pool_mana",
                        ChatFormatting.AQUA, CatalystItem.format(blockTag.getInt("mana"))));
            }
        }

        EssenceTier tier = EssenceTier.fromItem(event.getItemStack().getItem());
        if (tier == null || !tier.isEnabled()) {
            return;
        }

        event.getToolTip().add(line("tooltip.manaessencebridge.essence_sell",
                ChatFormatting.AQUA, CatalystItem.format(tier.getManaPerEssence())));
        event.getToolTip().add(line("tooltip.manaessencebridge.essence_pool",
                ChatFormatting.DARK_GRAY, tier.getLevel(), tier.getDisplayName()));
    }

    /**
     * Читает тир, который пул унёс с собой при поломке. Проверяем по нашему
     * ключу в BlockEntityTag, а не по id предмета: так подсказка работает
     * у любого вида пула, включая разбавленный, сказочный и аддонные.
     */
    private static EssenceTier storedTier(ItemStack stack) {
        CompoundTag blockTag = stack.getTagElement("BlockEntityTag");
        if (blockTag == null) {
            return null;
        }
        CompoundTag caps = blockTag.getCompound("ForgeCaps").getCompound(CAP_KEY);
        return caps.contains("tier") ? EssenceTier.byLevel(caps.getInt("tier")) : null;
    }

    private static Component line(String key, ChatFormatting color, Object... args) {
        MutableComponent c = Component.translatable(key, args);
        c.withStyle(color);
        return c;
    }
}
