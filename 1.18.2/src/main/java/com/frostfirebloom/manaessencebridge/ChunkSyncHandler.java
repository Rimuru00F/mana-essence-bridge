package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.botania.api.mana.IManaPool;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Когда игрок начинает видеть чанк, досылаем ему тиры всех прокачанных
 * пулов внутри - иначе HUD молчал бы до следующей установки катализатора.
 *
 * Заодно чиним раскраску. Цвет пула хранится в BlockEntity самой Botania
 * и выставляется один раз, при установке катализатора. Значит пулы,
 * прокачанные до смены палитры, остаются в старых цветах навсегда.
 * Здесь мы их перекрашиваем под текущий тир.
 */
public class ChunkSyncHandler {

    /**
     * Цвета, которые мод когда-либо ставил сам: текущая палитра плюс
     * два цвета из первой версии. Всё остальное - краситель, которым
     * пул покрасил игрок вручную, и такое мы не трогаем.
     */
    private static final Set<DyeColor> MANAGED = EnumSet.of(
            DyeColor.YELLOW, DyeColor.LIME, DyeColor.ORANGE, DyeColor.BLUE, DyeColor.RED,
            DyeColor.PURPLE, DyeColor.LIGHT_BLUE);

    @SubscribeEvent
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        LevelChunk chunk = event.getWorld().getChunk(event.getPos().x, event.getPos().z);

        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockEntity te = entry.getValue();
            if (!(te instanceof IManaPool)) {
                continue;
            }

            InferiumCatalystCapability cap =
                    te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
            if (cap == null || !cap.isUpgraded()) {
                continue;
            }

            ModNetwork.syncToPlayer(event.getPlayer(), entry.getKey(), cap);
            // Хозяин мог быть офлайн, пока автоматика гнала ману, - досчитываем пороги.
            PoolThroughput.recheck(event.getPlayer(), cap, ((IManaPool) te).getCurrentMana());
            // Страховка для автозабора: если чанк загрузился раньше, чем
            // были готовы его блок-сущности, регистрируем пул здесь.
            PoolAutoPull.track(event.getWorld(), entry.getKey());
            // Заодно подводим ёмкость под тир: так пулы из прошлых версий
            // получают увеличенную ёмкость, а смена конфига вступает в силу.
            PoolCapacity.apply(event.getWorld(), entry.getKey(), te, cap);
            // Игрок увидел пул - освежаем запись в Мана-гроссбухе.
            PoolLedger.update(event.getWorld(), entry.getKey(), cap, te);
            repaint(event.getWorld(), entry.getKey(), te, cap);
        }
    }

    private static void repaint(Level level, BlockPos pos, BlockEntity te, InferiumCatalystCapability cap) {
        EssenceTier tier = EssenceTier.byLevel(cap.getTier());
        if (tier == null) {
            return;
        }

        IManaPool pool = (IManaPool) te;
        DyeColor current = pool.getColor();
        if (current == tier.getPoolColor()) {
            return;
        }
        if (current != null && !MANAGED.contains(current)) {
            return;
        }

        pool.setColor(tier.getPoolColor());
        te.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }
}
