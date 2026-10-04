package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import com.frostfirebloom.manaessencebridge.PoolAutoPull;
import com.frostfirebloom.manaessencebridge.PoolCapacity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import vazkii.botania.api.mana.IManaPool;

/**
 * Подсказка на экране: наводишь прицел на прокачанный Mana Pool -
 * сверху появляется его тир, курс обмена и текущий запас маны.
 */
public class ClientHudHandler {

    private static final int COLOR_TITLE = 0xFFD9B3FF;
    private static final int COLOR_TEXT = 0xFFBBBBBB;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL
                || !BridgeConfig.showHud() || overlayModPresent()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null || mc.player == null || mc.gameSettings.hideGUI) {
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (!(hit instanceof BlockRayTraceResult) || hit.getType() != RayTraceResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = ((BlockRayTraceResult) hit).getPos();
        TileEntity te = mc.world.getTileEntity(pos);
        if (!(te instanceof IManaPool)) {
            return;
        }

        EssenceTier tier = EssenceTier.byLevel(ClientPoolTiers.get(pos));
        if (tier == null) {
            return;
        }

        IManaPool pool = (IManaPool) te;
        FontRenderer font = mc.fontRenderer;
        MatrixStack matrix = event.getMatrixStack();
        int centerX = mc.getMainWindow().getScaledWidth() / 2;
        int y = 6;

        String tierName = I18n.format(tier.getTranslationKey());

        drawCentered(font, matrix, I18n.format("hud.manaessencebridge.title", tier.getLevel(), tierName),
                centerX, y, COLOR_TITLE);
        y += 10;
        drawCentered(font, matrix, I18n.format("hud.manaessencebridge.rate", tierName,
                        CatalystItem.format(tier.getManaPerEssence()),
                        CatalystItem.format(tier.getManaCost())),
                centerX, y, COLOR_TEXT);
        y += 10;
        drawCentered(font, matrix, I18n.format("hud.manaessencebridge.stored",
                        CatalystItem.format(pool.getCurrentMana()),
                        CatalystItem.format(PoolCapacity.maxMana(te))),
                centerX, y, COLOR_TEXT);
        y += 10;
        drawCentered(font, matrix, I18n.format("hud.manaessencebridge.processed",
                        CatalystItem.format(ClientPoolTiers.getProcessed(pos))),
                centerX, y, COLOR_TEXT);
        y += 10;
        drawCentered(font, matrix, I18n.format(PoolAutoPull.statusKey(
                        ClientPoolTiers.isPullEnabled(pos), mc.world.isBlockPowered(pos))),
                centerX, y, COLOR_TEXT);
    }

    /**
     * Тиры сбрасываем при любой выгрузке клиентского мира, включая переход
     * в другое измерение: пулы Верхнего мира не должны всплыть в Незере
     * на тех же координатах. Сервер дошлёт их заново по чанкам.
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote()) {
            ClientPoolTiers.clear();
        }
    }

    /**
     * А маску гейтов - только при выходе с сервера. Сервер шлёт её лишь при
     * входе и при новом достижении, так что сброс при смене измерения прятал
     * катализаторы в JEI до перезахода.
     */
    @SubscribeEvent
    public void onLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
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

    private static void drawCentered(FontRenderer font, MatrixStack matrix, String text, int centerX, int y, int color) {
        font.drawStringWithShadow(matrix, text, centerX - font.getStringWidth(text) / 2.0F, y, color);
    }
}
