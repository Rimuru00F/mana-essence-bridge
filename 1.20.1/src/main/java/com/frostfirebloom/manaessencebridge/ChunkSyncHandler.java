package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.botania.api.mana.ManaPool;

import java.util.Map;

/**
 * Когда игрок начинает видеть чанк, досылаем ему тиры всех прокачанных
 * пулов внутри - иначе HUD молчал бы до следующей установки катализатора.
 */
public class ChunkSyncHandler {

    @SubscribeEvent
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        LevelChunk chunk = event.getLevel().getChunk(event.getPos().x, event.getPos().z);

        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockEntity te = entry.getValue();
            if (!(te instanceof ManaPool)) {
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
