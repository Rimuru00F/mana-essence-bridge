package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import vazkii.botania.api.mana.IManaPool;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Сеть пулов: свои прокачанные пулы связываются Зеркалом эссенции (оно
 * привязано к одному пулу, Shift+ПКМ им по другому - связать или разорвать),
 * и раз в секунду мана течёт из более полного пула в более пустой, пока оба
 * не заполнятся одинаково - на любом расстоянии в пределах измерения. В
 * Botania ману между пулами можно гнать только распределителями по прямой.
 *
 * Мана только переносится - не создаётся и не пропадает. Оба пула должны быть
 * загружены, сигнал редстоуна на любом из них ставит связь на паузу. Связь
 * хранится в обоих пулах; если второго пула больше нет или он забыл связь
 * (сломан, перенесён, снят тир), первый молча её убирает.
 */
public final class PoolNetwork {

    public static final int MAX_LINKS = 8;

    private PoolNetwork() {
    }

    private static final class Pool {
        final TileEntity te;
        final IManaPool pool;
        final InferiumCatalystCapability cap;

        Pool(TileEntity te, InferiumCatalystCapability cap) {
            this.te = te;
            this.pool = (IManaPool) te;
            this.cap = cap;
        }
    }

    /** Прокачанный пул в этой клетке (чанк подгружается - это действие игрока). */
    @Nullable
    private static Pool pool(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return te instanceof IManaPool && cap != null && cap.isUpgraded() ? new Pool(te, cap) : null;
    }

    /** Shift+ПКМ зеркалом, привязанным к from, по пулу to: связать, разорвать или показать связи. */
    static void toggle(World world, PlayerEntity player, BlockPos from, BlockPos to) {
        if (from.equals(to)) {
            describe(world, player, from);
            return;
        }
        Pool a = pool(world, from);
        Pool b = pool(world, to);
        if (a == null || b == null) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.link_not_pool");
            return;
        }
        if (!PoolAccess.mayUse(player, a.cap.getOwner()) || !PoolAccess.mayUse(player, b.cap.getOwner())) {
            status(player, TextFormatting.RED, "message.manaessencebridge.not_your_pool");
            return;
        }
        if (a.cap.hasLink(to)) {
            a.cap.removeLink(to);
            b.cap.removeLink(from);
            PoolLedger.update(world, from, a.cap, a.te);
            PoolLedger.update(world, to, b.cap, b.te);
            a.te.markDirty();
            b.te.markDirty();
            world.playSound(null, to, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.6F, 1.4F);
            status(player, TextFormatting.GRAY, "message.manaessencebridge.link_removed", to.getX(), to.getY(), to.getZ());
            return;
        }
        if (a.cap.getLinks().size() >= MAX_LINKS || b.cap.getLinks().size() >= MAX_LINKS) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.link_full", MAX_LINKS);
            return;
        }
        a.cap.addLink(to);
        b.cap.addLink(from);
        PoolLedger.update(world, from, a.cap, a.te);
        PoolLedger.update(world, to, b.cap, b.te);
        a.te.markDirty();
        b.te.markDirty();
        world.playSound(null, to, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.8F, 1.4F);
        EssenceTier tier = EssenceTier.byLevel(b.cap.getTier());
        if (tier != null) {
            PoolEffects.burst(world, to, tier, 20, 0.3);
        }
        status(player, TextFormatting.AQUA, "message.manaessencebridge.link_added",
                to.getX(), to.getY(), to.getZ(), a.cap.getLinks().size());
        if (player instanceof ServerPlayerEntity) {
            PoolThroughput.award((ServerPlayerEntity) player, "pool_network");
        }
    }

    /** Связи пула - в чат. */
    static void describe(World world, PlayerEntity player, BlockPos pos) {
        Pool a = pool(world, pos);
        if (a == null) {
            return;
        }
        List<BlockPos> links = a.cap.getLinks();
        if (links.isEmpty()) {
            status(player, TextFormatting.GRAY, "message.manaessencebridge.links_none");
            return;
        }
        player.sendMessage(new TranslationTextComponent("message.manaessencebridge.links_header", links.size())
                .mergeStyle(TextFormatting.AQUA), Util.DUMMY_UUID);
        for (BlockPos p : links) {
            player.sendMessage(new TranslationTextComponent("message.manaessencebridge.links_line", p.getX(), p.getY(), p.getZ())
                    .mergeStyle(TextFormatting.GRAY), Util.DUMMY_UUID);
        }
    }

    /** Раз в секунду: каждая связанная пара загруженных пулов выравнивает заполненность. */
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        World world = event.world;
        int rate = BridgeConfig.poolLinkRate();
        if (rate <= 0 || world.getGameTime() % 20 != 0) {
            return;
        }
        for (BlockPos pos : PoolAutoPull.tracked(world)) {
            if (!world.isBlockLoaded(pos)) {
                continue;
            }
            Pool a = pool(world, pos);
            if (a == null || a.cap.getLinks().isEmpty()) {
                continue;
            }
            for (BlockPos other : new ArrayList<>(a.cap.getLinks())) {
                // каждую пару - один раз, со стороны пула с меньшей позицией
                if (other.toLong() < pos.toLong() || !world.isBlockLoaded(other)) {
                    continue;
                }
                Pool b = pool(world, other);
                if (b == null || !b.cap.hasLink(pos)) {
                    a.cap.removeLink(other); // второго пула нет или он забыл связь
                    a.te.markDirty();
                    continue;
                }
                if (world.isBlockPowered(pos) || world.isBlockPowered(other)) {
                    continue;
                }
                balance(world, a, b, rate);
            }
        }
    }

    private static void balance(World world, Pool a, Pool b, int rate) {
        long capA = PoolCapacity.maxMana(a.te);
        long capB = PoolCapacity.maxMana(b.te);
        if (capA <= 0 || capB <= 0) {
            return;
        }
        long manaA = a.pool.getCurrentMana();
        long manaB = b.pool.getCurrentMana();
        // сколько должно остаться в A, чтобы оба пула были заполнены одинаково
        long targetA = (manaA + manaB) * capA / (capA + capB);
        long move = manaA - targetA;
        if (Math.abs(move) < 100) {
            return;
        }
        Pool from = move > 0 ? a : b;
        Pool to = move > 0 ? b : a;
        long space = (move > 0 ? capB : capA) - to.pool.getCurrentMana();
        long amount = Math.min(Math.min(Math.abs(move), rate), Math.min(space, from.pool.getCurrentMana()));
        if (amount <= 0) {
            return;
        }
        from.pool.receiveMana((int) -amount);
        to.pool.receiveMana((int) amount);
        from.te.markDirty();
        to.te.markDirty();
        EssenceTier tier = EssenceTier.byLevel(to.cap.getTier());
        if (tier != null) {
            PoolEffects.burst(world, to.te.getPos(), tier, 3, 0.2);
        }
    }

    private static void status(PlayerEntity player, TextFormatting color, String key, Object... args) {
        player.sendStatusMessage(new TranslationTextComponent(key, args).mergeStyle(color), true);
    }
}
