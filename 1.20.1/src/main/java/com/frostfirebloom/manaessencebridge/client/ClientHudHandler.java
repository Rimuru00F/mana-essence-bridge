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
import com.frostfirebloom.manaessencebridge.PoolAutoPull;
import com.frostfirebloom.manaessencebridge.PoolCapacity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
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
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()
                || !BridgeConfig.showHud() || overlayModPresent()) {
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
                        CatalystItem.format(pool.getCurrentMana()),
                        CatalystItem.format(PoolCapacity.maxMana(te))),
                centerX, y, COLOR_TEXT);
        y += 10;
        drawCentered(graphics, font, I18n.get("hud.manaessencebridge.processed",
                        CatalystItem.format(ClientPoolTiers.getProcessed(pos))),
                centerX, y, COLOR_TEXT);
        y += 10;
        drawCentered(graphics, font, I18n.get(PoolAutoPull.statusKey(
                        ClientPoolTiers.isPullEnabled(pos), mc.level.hasNeighborSignal(pos))),
                centerX, y, COLOR_TEXT);
    }

    /**
     * Тиры сбрасываем при любой выгрузке клиентского мира, включая переход
     * в другое измерение: пулы Верхнего мира не должны всплыть в Незере
     * на тех же координатах. Сервер дошлёт их заново по чанкам.
     */
    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientPoolTiers.clear();
        }
    }

    /**
     * А маску гейтов - только при выходе с сервера. Сервер шлёт её лишь при
     * входе и при новом достижении, так что сброс при смене измерения прятал
     * катализаторы в JEI до перезахода.
     */
    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGates.clear();
    }


    /**
     * Свой HUD нужен только тем, у кого нет Jade или The One Probe.
     * Иначе игрок видел бы одно и то же дважды: наш текст сверху экрана
     * и строку в оверлее. Отключить нашу строку в Jade или TOP игрок
     * может их же настройками, поэтому третьего переключателя не завожу.
     */
    private static Boolean overlayModPresent;

    private static boolean overlayModPresent() {
        if (overlayModPresent == null) {
            overlayModPresent = ModList.get().isLoaded("jade")
                    || ModList.get().isLoaded("theoneprobe");
        }
        return overlayModPresent;
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color);
    }
}
