package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * В 1.18.2 прозрачность модели («render_type» в json) ещё не поддерживается -
 * цветкам и их парящим вариантам нужен слой cutout, иначе вокруг лепестков
 * будет чёрный фон.
 */
public final class FlowerRenderLayers {

    private FlowerRenderLayers() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RegistryObject<Block> block : List.of(
                    ModBlocks.MYSTICARNATION, ModBlocks.REAPERBLOOM, ModBlocks.WARDENIA, ModBlocks.ESSENTIDE,
                    ModBlocks.BROOKBELL, ModBlocks.BOLTBLOOM, ModBlocks.MELODIA, ModBlocks.BUMBLEBLOOM,
                    ModBlocks.FLOATING_MYSTICARNATION, ModBlocks.FLOATING_REAPERBLOOM, ModBlocks.FLOATING_WARDENIA,
                    ModBlocks.FLOATING_ESSENTIDE, ModBlocks.FLOATING_BROOKBELL, ModBlocks.FLOATING_BOLTBLOOM,
                    ModBlocks.FLOATING_MELODIA, ModBlocks.FLOATING_BUMBLEBLOOM)) {
                ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout());
            }
        });
    }
}
