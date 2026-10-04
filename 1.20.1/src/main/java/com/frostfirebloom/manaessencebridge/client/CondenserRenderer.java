package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.CakeDay;
import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.frostfirebloom.manaessencebridge.ModItems;
import com.frostfirebloom.manaessencebridge.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Рисует над Конденсатором эссенцию, которая лежит у него внутри: медленно
 * вращается и покачивается над приёмным отверстием, как предмет на пьедестале.
 */
public class CondenserRenderer implements BlockEntityRenderer<EssenceCondenserBlockEntity> {

    /** Праздничный тортик 17 октября; создаётся при первом показе, когда реестр уже готов. */
    private static ItemStack cake = ItemStack.EMPTY;


    public CondenserRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.ESSENCE_CONDENSER_BE.get(), CondenserRenderer::new);
    }

    @Override
    public void render(EssenceCondenserBlockEntity condenser, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = condenser.getDisplayedStack();
        Level level = condenser.getLevel();
        if (stack.isEmpty() && CakeDay.isToday()) {
            if (cake.isEmpty()) {
                cake = new ItemStack(ModItems.BIRTHDAY_CAKE.get());
            }
            stack = cake; // 17 октября над пустым конденсатором парит тортик
        }
        if (stack.isEmpty() || level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 1.2 + Math.sin(time / 10.0) * 0.05, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.0F % 360.0F));
        pose.scale(0.6F, 0.6F, 0.6F);
        int above = LevelRenderer.getLightColor(level, condenser.getBlockPos().above());
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, above,
                OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }
}
