package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.spark.ISparkAttachable;

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

    private static final Map<RegistryKey<World>, Set<BlockPos>> TRACKED = new HashMap<>();

    public static void track(World world, BlockPos pos) {
        if (world.isRemote) {
            return;
        }
        TRACKED.computeIfAbsent(world.getDimensionKey(), k -> new HashSet<>()).add(pos.toImmutable());
    }

    public static void untrack(World world, BlockPos pos) {
        if (world.isRemote) {
            return;
        }
        Set<BlockPos> set = TRACKED.get(world.getDimensionKey());
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
        IWorld iWorld = event.getWorld();
        if (iWorld == null || iWorld.isRemote() || !(iWorld instanceof World)) {
            return;
        }
        IChunk chunk = event.getChunk();
        for (BlockPos pos : chunk.getTileEntitiesPos()) {
            // Только из самого чанка, не через мир. В 1.19.2 и 1.20.1 запрос к миру
            // во время загрузки чанка снова просит загрузить этот же чанк, и игра
            // зависает на первом чанке с сундуком или спавнером.
            TileEntity te = chunk.getTileEntity(pos);
            if (te instanceof IManaPool && isUpgraded(te)) {
                track((World) iWorld, pos);
            }
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        IWorld iWorld = event.getWorld();
        if (iWorld == null || iWorld.isRemote() || !(iWorld instanceof World)) {
            return;
        }
        Set<BlockPos> set = TRACKED.get(((World) iWorld).getDimensionKey());
        if (set == null) {
            return;
        }
        ChunkPos cp = event.getChunk().getPos();
        set.removeIf(pos -> pos.getX() >> 4 == cp.x && pos.getZ() >> 4 == cp.z);
    }

    /** Реестр статический: без очистки позиции одного мира перетекли бы в следующий. */
    @SubscribeEvent
    public void onServerStopped(FMLServerStoppedEvent event) {
        TRACKED.clear();
    }

    // --- сам обход ---------------------------------------------------------

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!BridgeConfig.pullFromContainers()) {
            return;
        }
        World world = event.world;
        if (world.getGameTime() % BridgeConfig.pullIntervalTicks() != 0) {
            return;
        }
        Set<BlockPos> set = TRACKED.get(world.getDimensionKey());
        if (set == null || set.isEmpty()) {
            return;
        }

        Iterator<BlockPos> it = set.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!world.isBlockPresent(pos)) {
                continue; // чанк выгружен - позицию оставляем, вернётся вместе с чанком
            }
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof IManaPool)) {
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
            if (world instanceof ServerWorld && !((ServerWorld) world).getChunkProvider().canTick(pos)) {
                continue;
            }
            // Сигнал редстоуна ставит автозабор на паузу, как у воронки.
            if (world.isBlockPowered(pos)) {
                continue;
            }
            pullInto(world, pos, te, (IManaPool) te, cap);
        }
    }

    private static void pullInto(World world, BlockPos pos, TileEntity te, IManaPool pool,
                                 InferiumCatalystCapability cap) {
        int space = availableSpace(te);
        if (space <= 0) {
            return;
        }

        long pulled = 0L;
        int items = 0;
        EssenceTier shown = null;

        for (Direction dir : Direction.values()) {
            BlockPos npos = pos.offset(dir);
            // Не заглядываем в незагруженный чанк: getTileEntity загрузил бы
            // его ради одной проверки - и так каждую секунду.
            if (!world.isBlockPresent(npos)) {
                continue;
            }
            TileEntity neighbour = world.getTileEntity(npos);
            if (neighbour == null || neighbour instanceof IManaPool) {
                continue; // из другого пула не тянем - у него и так нечего отдать
            }
            if (neighbour instanceof EssenceCondenserBlockEntity) {
                continue; // иначе пул и конденсатор гоняли бы эссенцию по кругу, сжигая ману
            }
            IItemHandler inv = neighbour
                    .getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir.getOpposite())
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
        te.markDirty();
        BlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
        PoolThroughput.record(world, pos, cap, pulled, null, true);
        PoolEffects.burst(world, pos, shown, Math.min(2 + items, 8), 0.25);
    }

    private static boolean isUpgraded(TileEntity te) {
        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return cap != null && cap.isUpgraded();
    }

    private static int availableSpace(TileEntity te) {
        if (te instanceof ISparkAttachable) {
            return Math.max(0, ((ISparkAttachable) te).getAvailableSpaceForMana());
        }
        return Integer.MAX_VALUE;
    }
}
