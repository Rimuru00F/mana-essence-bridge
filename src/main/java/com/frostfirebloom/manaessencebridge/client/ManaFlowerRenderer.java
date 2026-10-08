package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.World;
import net.minecraftforge.client.model.data.IModelData;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntitySpecialFlower;
import vazkii.botania.client.render.tile.RenderTileSpecialFlower;
import vazkii.botania.common.item.equipment.bauble.ItemMonocle;

/**
 * Рендерер цветков мода - делает то же, что рендерер цветков Botania:
 *
 * 1) Парящие цветки медленно вращаются, покачиваются и чуть наклоняются
 *    (та же математика, у каждого цветка своя фаза). Блок в мире невидим
 *    (ENTITYBLOCK_ANIMATED), модель с островком рисуется здесь каждый кадр.
 *    Опция animateFloatingFlowers выключает анимацию.
 * 2) С надетым Моноклем Манасира показывает радиус цветка - при взгляде на
 *    него или пока игрок привязывает его жезлом.
 *
 * Для радиуса вызываются публичные статические методы Botania (монокль,
 * привязка, рисование рамки) - они не входят в её API. Кода Botania в моде
 * нет, только вызовы; если в другой версии Botania их не окажется, радиус
 * просто перестанет рисоваться, а игра не упадёт.
 */
public class ManaFlowerRenderer extends TileEntityRenderer<TileEntitySpecialFlower> {

    /** Методы Botania для радиуса нашлись; после первой ошибки - больше не пробуем. */
    private static boolean radiusAvailable = true;

    public ManaFlowerRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(TileEntitySpecialFlower flower, float partialTicks, MatrixStack ms,
                       IRenderTypeBuffer buffers, int light, int overlay) {
        World world = flower.getWorld();
        if (world == null) {
            return;
        }
        if (flower.isFloating() && BridgeConfig.animateFloatingFlowers()) {
            renderFloating(flower, world, partialTicks, ms, buffers, light, overlay);
        }
        if (radiusAvailable) {
            try {
                renderRadius(flower, ms, buffers);
            } catch (LinkageError e) {
                radiusAvailable = false;
                ManaEssenceBridge.LOGGER.warn("Botania's flower radius helpers are missing - the Manaseer Monocle won't show radii of this mod's flowers", e);
            }
        }
    }

    private static void renderFloating(TileEntitySpecialFlower flower, World world, float partialTicks, MatrixStack ms,
                                       IRenderTypeBuffer buffers, int light, int overlay) {
        BlockState state = flower.getBlockState();
        BlockPos pos = flower.getPos();
        double t = world.getGameTime() + partialTicks + Math.floorMod(pos.hashCode(), 1000);
        ms.push();
        ms.translate(0.5, 0, 0.5);
        ms.rotate(Vector3f.YP.rotationDegrees((float) -t * 0.5F));
        ms.translate(-0.5, Math.sin(t * 0.05) * 0.1, 0.5);
        ms.rotate(Vector3f.XP.rotationDegrees(4F * (float) Math.sin(t * 0.04)));
        ms.rotate(Vector3f.YP.rotationDegrees(90F));
        BlockRendererDispatcher blocks = Minecraft.getInstance().getBlockRendererDispatcher();
        IBakedModel model = blocks.getModelForState(state);
        // островок: загрузчик Botania берёт его тип из блок-сущности
        IModelData data = model.getModelData(world, pos, state, flower.getModelData());
        blocks.getBlockModelRenderer().renderModel(ms.getLast(), buffers.getBuffer(RenderTypeLookup.func_239220_a_(state, false)),
                state, model, 1F, 1F, 1F, light, overlay, data);
        ms.pop();
    }

    /** Как у цветков Botania: монокль надет, и игрок смотрит на цветок или привязывает его жезлом. */
    private static void renderRadius(TileEntitySpecialFlower flower, MatrixStack ms, IRenderTypeBuffer buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.renderViewEntity instanceof LivingEntity) || !ItemMonocle.hasMonocle((LivingEntity) mc.renderViewEntity)) {
            return;
        }
        LivingEntity view = (LivingEntity) mc.renderViewEntity;
        BlockPos target = mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK
                ? ((BlockRayTraceResult) mc.objectMouseOver).getPos() : null;
        boolean binding = RenderTileSpecialFlower.hasBindingAttempt(view, flower.getPos());
        if (!binding && !flower.getPos().equals(target)) {
            return;
        }
        ms.push();
        if (binding) {
            ms.translate(0, 0.005, 0);
        }
        drawRadius(flower, ms, buffers, flower.getRadius());
        ms.translate(0, 0.002, 0);
        drawRadius(flower, ms, buffers, flower.getSecondaryRadius());
        ms.pop();
    }

    private static void drawRadius(TileEntitySpecialFlower flower, MatrixStack ms, IRenderTypeBuffer buffers, RadiusDescriptor radius) {
        if (radius != null) {
            RenderTileSpecialFlower.renderRadius(flower, ms, buffers, radius);
        }
    }
}
