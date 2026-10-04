package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD Конденсатора при взгляде на него с Жезлом леса в руке - как у
 * распределителей и пулов Botania: что делает, резерв, пул, сколько внутри
 * и как привязать.
 */
public final class CondenserHud {

    private CondenserHud() {
    }

    public static void render(PoseStack graphics, Minecraft mc, EssenceCondenserBlockEntity condenser) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(Component.translatable("block.manaessencebridge.essence_condenser").getString());
        colors.add(COLOR_TITLE);
        for (Component line : condenser.infoLines(mc.level)) {
            lines.add(line.getString());
            colors.add(COLOR_TEXT);
        }
        lines.add(Component.translatable("hud.manaessencebridge.condenser_reserve_hint").getString());
        colors.add(COLOR_HINT);
        lines.add(Component.translatable("hud.manaessencebridge.condenser_bind_hint").getString());
        colors.add(COLOR_HINT);

        Font font = mc.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 14;
        GuiComponent.fill(graphics, centerX - width / 2 - 4, y - 3, centerX + width / 2 + 4,
                y + lines.size() * 10 + 1, COLOR_BACK);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            font.drawShadow(graphics, line, centerX - font.width(line) / 2.0F, y, colors.get(i));
            y += 10;
        }
    }

    private static final int COLOR_TITLE = 0xFFD9B3FF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFA0A0A0;
    private static final int COLOR_BACK = 0x90000000;
}
