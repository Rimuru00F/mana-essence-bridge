package com.frostfirebloom.manaessencebridge;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.botania.api.mana.IManaPool;

import java.util.Map;

/**
 * Когда игрок начинает видеть чанк, досылаем ему тиры всех прокачанных
 * пулов внутри - иначе HUD молчал бы до следующей установки катализатора.
 */
public class ChunkSyncHandler {

    @SubscribeEvent
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        Chunk chunk = event.getWorld().getChunk(event.getPos().x, event.getPos().z);

        for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
            TileEntity te = entry.getValue();
            if (!(te instanceof IManaPool)) {
                continue;
            }

            InferiumCatalystCapability cap =
                    te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
            if (cap != null && cap.isUpgraded()) {
                ModNetwork.syncToPlayer(event.getPlayer(), entry.getKey(), cap.getTier());
            }
        }
    }
}
