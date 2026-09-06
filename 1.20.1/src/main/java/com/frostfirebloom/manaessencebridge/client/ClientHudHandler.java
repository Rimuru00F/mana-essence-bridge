package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.botania.api.mana.ManaPool;

/**
 * Подсказка на экране: наводишь прицел на прокачанный Mana Pool -
 * сверху появляется его тир, курс обмена и текущий запас маны.
 *
 * В 1.20.1 отрисовка идёт через GuiGraphics, а событие оверлея разделено
 * по элементам - цепляемся к прицелу, он рисуется всегда.
 */
public class ClientHudHandler {

    private static final int COLOR_TITLE = 0xFFD9B3FF;
    private static final int COLOR_TEXT = 0xFFBBBBBB;

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type() || !BridgeConfig.showHud()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockEntity te = mc.level.getBlockEntity(pos);
        if (!(te instanceof ManaPool)) {
            return;
        }

        EssenceTier tier = EssenceTier.byLevel(ClientPoolTiers.get(pos));
        if (tier == null) {
            return;
        }

        ManaPool pool = (ManaPool) te;
        Font font = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int y = 6;

        String tierName = I18n.get(tier.getTranslationKey());

        drawCentered(graphics, font, I18n.get("hud.manaessencebridge.title", tier.getLevel(), tierName),
                centerX, y, COLOR_TITLE);
        y += 10;
        drawCentered(graphics, font, I18n.get("hud.manaessencebridge.rate", tierName,
                        CatalystItem.format(tier.getManaPerEssence()),
                        CatalystItem.format(tier.getManaCost())),
                centerX, y, COLOR_TEXT);
        y += 10;
        drawCentered(graphics, font, I18n.get("hud.manaessencebridge.stored",
                        CatalystItem.format(pool.getCurrentMana())),
                centerX, y, COLOR_TEXT);
    }

    /** Чтобы тиры одного мира не показывались в другом. */
    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientPoolTiers.clear();
        }
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color);
    }
}
