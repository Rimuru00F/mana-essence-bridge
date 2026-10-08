package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.ModBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * В 1.16.5 прозрачность модели не задаётся в json - цветкам нужен слой
 * cutout, иначе вокруг лепестков будет чёрный фон.
 */
public final class FlowerRenderLayers {

    private FlowerRenderLayers() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            RenderTypeLookup.setRenderLayer(ModBlocks.MYSTICARNATION.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.REAPERBLOOM.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.WARDENIA.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.ESSENTIDE.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.BROOKBELL.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.BOLTBLOOM.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.MELODIA.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.BUMBLEBLOOM.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_MYSTICARNATION.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_REAPERBLOOM.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_WARDENIA.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_ESSENTIDE.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_BROOKBELL.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_BOLTBLOOM.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_MELODIA.get(), RenderType.getCutout());
            RenderTypeLookup.setRenderLayer(ModBlocks.FLOATING_BUMBLEBLOOM.get(), RenderType.getCutout());
            // Эссенция, парящая над конденсатором
            ClientRegistry.bindTileEntityRenderer(ModBlocks.ESSENCE_CONDENSER_BE.get(), CondenserRenderer::new);
            // Покачивание парящих цветков и радиус под моноклем
            ClientRegistry.bindTileEntityRenderer(ModBlocks.MYSTICARNATION_BE.get(), ManaFlowerRenderer::new);
            ClientRegistry.bindTileEntityRenderer(ModBlocks.REAPERBLOOM_BE.get(), ManaFlowerRenderer::new);
            ClientRegistry.bindTileEntityRenderer(ModBlocks.WARDENIA_BE.get(), ManaFlowerRenderer::new);
            ClientRegistry.bindTileEntityRenderer(ModBlocks.ESSENTIDE_BE.get(), ManaFlowerRenderer::new);
            ClientRegistry.bindTileEntityRenderer(ModBlocks.BROOKBELL_BE.get(), ManaFlowerRenderer::new);
            ClientRegistry.bindTileEntityRenderer(ModBlocks.BOLTBLOOM_BE.get(), ManaFlowerRenderer::new);
            ClientRegistry.bindTileEntityRenderer(ModBlocks.MELODIA_BE.get(), ManaFlowerRenderer::new);
            ClientRegistry.bindTileEntityRenderer(ModBlocks.BUMBLEBLOOM_BE.get(), ManaFlowerRenderer::new);
        });
    }
}
