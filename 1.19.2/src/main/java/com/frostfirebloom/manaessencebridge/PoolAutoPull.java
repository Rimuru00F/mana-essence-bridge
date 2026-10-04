package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.mana.ManaPool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Прокачанный пул сам забирает эссенцию из соседних инвентарей.
 *
 * Работает только там, где игрок включил это Сифоном эссенции, и только пока
 * на пул не подан сигнал редстоуна - как замок у воронки. По умолчанию
 * выключено: пул, стоявший рядом с сундуком эссенции ещё до обновления,
 * не должен молча начать его опустошать.
 *
 * Тик у пула не наш - это блок Botania, - поэтому держим собственный
 * реестр прокачанных пулов и обходим только его. Позиции попадают сюда
 * при установке катализатора, установке пула, загрузке чанка; уходят при
 * поломке, снятии последнего тира и выгрузке чанка. На всякий случай
 * каждая позиция ещё и перепроверяется перед работой.
 *
 * Шаг обхода задаётся конфигом, по умолчанию раз в секунду: этого хватает,
 * чтобы сундук опустошался быстрее, чем наполняется любая ферма, и при этом
 * не дёргать инвентари каждый тик.
 */
public class PoolAutoPull {

    private static final Map<ResourceKey<Level>, Set<BlockPos>> TRACKED = new HashMap<>();

    public static void track(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        TRACKED.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void untrack(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        Set<BlockPos> set = TRACKED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /** Ключ строки состояния для HUD, Jade и TOP. */
    public static String statusKey(boolean enabled, boolean powered) {
        if (!enabled) {
            return "hud.manaessencebridge.pull_off";
        }
        return powered ? "hud.manaessencebridge.pull_paused" : "hud.manaessencebridge.pull_on";
    }

    // --- регистрация по чанкам ---------------------------------------------

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor accessor = event.getLevel();
        if (accessor == null || accessor.isClientSide() || !(accessor instanceof Level)) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            // Только из самого чанка, не через мир. В 1.19.2 и 1.20.1 запрос к миру
            // во время загрузки чанка снова просит загрузить этот же чанк, и игра
            // зависает на первом чанке с сундуком или спавнером.
            BlockEntity te = chunk.getBlockEntity(pos);
            if (te instanceof ManaPool && isUpgraded(te)) {
                track((Level) accessor, pos);
            }
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        LevelAccessor accessor = event.getLevel();
        if (accessor == null || accessor.isClientSide() || !(accessor instanceof Level)) {
            return;
        }
        Set<BlockPos> set = TRACKED.get(((Level) accessor).dimension());
        if (set == null) {
            return;
        }
        ChunkPos cp = event.getChunk().getPos();
        set.removeIf(pos -> pos.getX() >> 4 == cp.x && pos.getZ() >> 4 == cp.z);
    }

    /** Реестр статический: без очистки позиции одного мира перетекли бы в следующий. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        TRACKED.clear();
    }

    // --- сам обход ---------------------------------------------------------

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!BridgeConfig.pullFromContainers()) {
            return;
        }
        Level level = event.level;
        if (level.getGameTime() % BridgeConfig.pullIntervalTicks() != 0) {
            return;
        }
        Set<BlockPos> set = TRACKED.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }

        Iterator<BlockPos> it = set.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos)) {
                continue; // чанк выгружен - позицию оставляем, вернётся вместе с чанком
            }
            BlockEntity te = level.getBlockEntity(pos);
            if (!(te instanceof ManaPool)) {
                it.remove();
                continue;
            }
            InferiumCatalystCapability cap =
                    te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
            if (cap == null || !cap.isUpgraded()) {
                it.remove();
                continue;
            }
            if (!cap.isPullEnabled()) {
                continue; // сифоном не включали
            }
            // Чанк на краю загруженной зоны: блоки в нём не тикают, и пул
            // работал бы там, где стоят даже ванильные воронки.
            if (!level.shouldTickBlocksAt(pos)) {
                continue;
            }
            // Сигнал редстоуна ставит автозабор на паузу, как у воронки.
            if (level.hasNeighborSignal(pos)) {
                continue;
            }
            pullInto(level, pos, te, (ManaPool) te, cap);
        }
    }

    private static void pullInto(Level level, BlockPos pos, BlockEntity te, ManaPool pool,
                                 InferiumCatalystCapability cap) {
        int space = Math.max(0, pool.getMaxMana() - pool.getCurrentMana());
        if (space <= 0) {
            return;
        }

        long pulled = 0L;
        int items = 0;
        EssenceTier shown = null;

        for (Direction dir : Direction.values()) {
            BlockPos npos = pos.relative(dir);
            // Не заглядываем в незагруженный чанк: getBlockEntity загрузил бы
            // его ради одной проверки - и так каждую секунду.
            if (!level.isLoaded(npos)) {
                continue;
            }
            BlockEntity neighbour = level.getBlockEntity(npos);
            if (neighbour == null || neighbour instanceof ManaPool) {
                continue; // из другого пула не тянем - у него и так нечего отдать
            }
            if (neighbour instanceof EssenceCondenserBlockEntity) {
                continue; // иначе пул и конденсатор гоняли бы эссенцию по кругу, сжигая ману
            }
            IItemHandler inv = neighbour
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite())
                    .orElse(null);
            if (inv == null) {
                continue;
            }

            for (int slot = 0; slot < inv.getSlots() && space > 0; slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                EssenceTier tier = EssenceTier.fromItem(stack.getItem());
                if (tier == null || !tier.isEnabled() || !cap.supports(tier)) {
                    continue;
                }
                int manaPer = tier.getManaPerEssence();
                int want = Math.min(stack.getCount(), space / manaPer);
                if (want <= 0) {
                    continue;
                }
                ItemStack taken = inv.extractItem(slot, want, false);
                if (taken.isEmpty()) {
                    continue;
                }
                int mana = manaPer * taken.getCount();
                pool.receiveMana(mana);
                space -= mana;
                pulled += mana;
                items += taken.getCount();
                if (shown == null || tier.getLevel() > shown.getLevel()) {
                    shown = tier;
                }
            }
        }

        if (pulled <= 0) {
            return;
        }
        // Всё, что забрано за проход, отмечаем разом: один пакет и один всплеск
        // вместо отдельного на каждый слот сундука.
        te.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
        PoolThroughput.record(level, pos, cap, pulled, null, true);
        PoolEffects.burst(level, pos, shown, Math.min(2 + items, 8), 0.25);
    }

    private static boolean isUpgraded(BlockEntity te) {
        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return cap != null && cap.isUpgraded();
    }
}
