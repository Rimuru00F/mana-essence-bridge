package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

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

    public static void render(MatrixStack matrix, Minecraft mc, EssenceCondenserBlockEntity condenser) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(new TranslationTextComponent("block.manaessencebridge.essence_condenser").getString());
        colors.add(COLOR_TITLE);
        for (ITextComponent line : condenser.infoLines(mc.world)) {
            lines.add(line.getString());
            colors.add(COLOR_TEXT);
        }
        lines.add(new TranslationTextComponent("hud.manaessencebridge.condenser_reserve_hint").getString());
        colors.add(COLOR_HINT);
        lines.add(new TranslationTextComponent("hud.manaessencebridge.condenser_bind_hint").getString());
        colors.add(COLOR_HINT);

        FontRenderer font = mc.fontRenderer;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        int centerX = mc.getMainWindow().getScaledWidth() / 2;
        int y = mc.getMainWindow().getScaledHeight() / 2 + 14;
        AbstractGui.fill(matrix, centerX - width / 2 - 4, y - 3, centerX + width / 2 + 4,
                y + lines.size() * 10 + 1, COLOR_BACK);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            font.drawStringWithShadow(matrix, line, centerX - font.getStringWidth(line) / 2.0F, y, colors.get(i));
            y += 10;
        }
    }

    private static final int COLOR_TITLE = 0xFFD9B3FF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFA0A0A0;
    private static final int COLOR_BACK = 0x90000000;
}
