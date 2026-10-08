package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.frostfirebloom.manaessencebridge.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.model.data.ModelData;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.block_entity.SpecialFlowerBlockEntity;
import vazkii.botania.client.render.block_entity.SpecialFlowerBlockEntityRenderer;
import vazkii.botania.common.item.equipment.bauble.ManaseerMonocleItem;

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
public class ManaFlowerRenderer implements BlockEntityRenderer<SpecialFlowerBlockEntity> {

    /** Методы Botania для радиуса нашлись; после первой ошибки - больше не пробуем. */
    private static boolean radiusAvailable = true;

    public ManaFlowerRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.MYSTICARNATION_BE.get(), ManaFlowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.REAPERBLOOM_BE.get(), ManaFlowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.WARDENIA_BE.get(), ManaFlowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.ESSENTIDE_BE.get(), ManaFlowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.BROOKBELL_BE.get(), ManaFlowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.BOLTBLOOM_BE.get(), ManaFlowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.MELODIA_BE.get(), ManaFlowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.BUMBLEBLOOM_BE.get(), ManaFlowerRenderer::new);
    }

    @Override
    public void render(SpecialFlowerBlockEntity flower, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = flower.getLevel();
        if (level == null) {
            return;
        }
        if (flower.isFloating() && BridgeConfig.animateFloatingFlowers()) {
            renderFloating(flower, level, partialTick, pose, buffers, light, overlay);
        }
        if (radiusAvailable) {
            try {
                renderRadius(flower, pose, buffers);
            } catch (LinkageError e) {
                radiusAvailable = false;
                ManaEssenceBridge.LOGGER.warn("Botania's flower radius helpers are missing - the Manaseer Monocle won't show radii of this mod's flowers", e);
            }
        }
    }

    private static void renderFloating(SpecialFlowerBlockEntity flower, Level level, float partialTick, PoseStack pose,
                                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = flower.getBlockState();
        BlockPos pos = flower.getBlockPos();
        double t = level.getGameTime() + partialTick + Math.floorMod(pos.hashCode(), 1000);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Vector3f.YP.rotationDegrees((float) -t * 0.5F));
        pose.translate(-0.5, Math.sin(t * 0.05) * 0.1, 0.5);
        pose.mulPose(Vector3f.XP.rotationDegrees(4F * (float) Math.sin(t * 0.04)));
        pose.mulPose(Vector3f.YP.rotationDegrees(90F));
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blocks.getBlockModel(state);
        // островок: загрузчик Botania берёт его тип из блок-сущности
        ModelData data = model.getModelData(level, pos, state, ModelData.EMPTY);
        for (RenderType type : model.getRenderTypes(state, RandomSource.create(42), data)) {
            blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(type, false)),
                    state, model, 1F, 1F, 1F, light, overlay, data, type);
        }
        pose.popPose();
    }

    /** Как у цветков Botania: монокль надет, и игрок смотрит на цветок или привязывает его жезлом. */
    private static void renderRadius(SpecialFlowerBlockEntity flower, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.cameraEntity instanceof LivingEntity) || !ManaseerMonocleItem.hasMonocle((LivingEntity) mc.cameraEntity)) {
            return;
        }
        LivingEntity view = (LivingEntity) mc.cameraEntity;
        BlockPos target = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                ? ((BlockHitResult) mc.hitResult).getBlockPos() : null;
        boolean binding = SpecialFlowerBlockEntityRenderer.hasBindingAttempt(view, flower.getBlockPos());
        if (!binding && !flower.getBlockPos().equals(target)) {
            return;
        }
        pose.pushPose();
        if (binding) {
            pose.translate(0, 0.005, 0);
        }
        drawRadius(flower, pose, buffers, flower.getRadius());
        pose.translate(0, 0.002, 0);
        drawRadius(flower, pose, buffers, flower.getSecondaryRadius());
        pose.popPose();
    }

    private static void drawRadius(SpecialFlowerBlockEntity flower, PoseStack pose, MultiBufferSource buffers, RadiusDescriptor radius) {
        if (radius != null) {
            SpecialFlowerBlockEntityRenderer.renderRadius(flower, pose, buffers, radius);
        }
    }
}
