package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.CakeDay;
import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.frostfirebloom.manaessencebridge.ModItems;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.World;

/**
 * Рисует над Конденсатором эссенцию, которая лежит у него внутри: медленно
 * вращается и покачивается над приёмным отверстием, как предмет на пьедестале.
 */
public class CondenserRenderer extends TileEntityRenderer<EssenceCondenserBlockEntity> {

    /** Праздничный тортик 17 октября; создаётся при первом показе, когда реестр уже готов. */
    private static ItemStack cake = ItemStack.EMPTY;


    public CondenserRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(EssenceCondenserBlockEntity condenser, float partialTicks, MatrixStack matrix,
                       IRenderTypeBuffer buffers, int light, int overlay) {
        ItemStack stack = condenser.getDisplayedStack();
        World world = condenser.getWorld();
        if (stack.isEmpty() && CakeDay.isToday()) {
            if (cake.isEmpty()) {
                cake = new ItemStack(ModItems.BIRTHDAY_CAKE.get());
            }
            stack = cake; // 17 октября над пустым конденсатором парит тортик
        }
        if (stack.isEmpty() || world == null) {
            return;
        }
        float time = world.getGameTime() + partialTicks;
        matrix.push();
        matrix.translate(0.5, 1.2 + Math.sin(time / 10.0) * 0.05, 0.5);
        matrix.rotate(Vector3f.YP.rotationDegrees(time * 2.0F % 360.0F));
        matrix.scale(0.6F, 0.6F, 0.6F);
        int above = WorldRenderer.getCombinedLight(world, condenser.getPos().up());
        Minecraft.getInstance().getItemRenderer().renderItem(stack, ItemCameraTransforms.TransformType.FIXED, above,
                OverlayTexture.NO_OVERLAY, matrix, buffers);
        matrix.pop();
    }
}
