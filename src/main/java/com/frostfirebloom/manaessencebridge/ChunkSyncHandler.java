package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.item.DyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IThrottledPacket;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Когда игрок начинает видеть чанк, досылаем ему тиры всех прокачанных
 * пулов внутри - иначе HUD молчал бы до следующей установки катализатора.
 *
 * Заодно чиним раскраску. Цвет пула хранится в TileEntity самой Botania
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
        Chunk chunk = event.getWorld().getChunk(event.getPos().x, event.getPos().z);

        for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
            TileEntity te = entry.getValue();
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

    private static void repaint(World world, BlockPos pos, TileEntity te, InferiumCatalystCapability cap) {
        EssenceTier tier = EssenceTier.byLevel(cap.getTier());
        if (tier == null) {
            return;
        }

        IManaPool pool = (IManaPool) te;
        DyeColor current = pool.getColor();
        if (current == tier.getPoolColor() || !MANAGED.contains(current)) {
            return;
        }

        pool.setColor(tier.getPoolColor());
        te.markDirty();
        BlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);

        // Пул сам управляет рассылкой своего состояния - просим отправить.
        if (te instanceof IThrottledPacket) {
            ((IThrottledPacket) te).markDispatchable();
        }
    }
}
