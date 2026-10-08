package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import vazkii.botania.api.mana.ManaPool;

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
        final BlockEntity te;
        final ManaPool pool;
        final InferiumCatalystCapability cap;

        Pool(BlockEntity te, InferiumCatalystCapability cap) {
            this.te = te;
            this.pool = (ManaPool) te;
            this.cap = cap;
        }
    }

    /** Прокачанный пул в этой клетке (чанк подгружается - это действие игрока). */
    @Nullable
    private static Pool pool(Level level, BlockPos pos) {
        BlockEntity te = level.getBlockEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return te instanceof ManaPool && cap != null && cap.isUpgraded() ? new Pool(te, cap) : null;
    }

    /** Shift+ПКМ зеркалом, привязанным к from, по пулу to: связать, разорвать или показать связи. */
    static void toggle(Level level, Player player, BlockPos from, BlockPos to) {
        if (from.equals(to)) {
            describe(level, player, from);
            return;
        }
        Pool a = pool(level, from);
        Pool b = pool(level, to);
        if (a == null || b == null) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.link_not_pool");
            return;
        }
        if (!PoolAccess.mayUse(player, a.cap.getOwner()) || !PoolAccess.mayUse(player, b.cap.getOwner())) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.not_your_pool");
            return;
        }
        if (a.cap.hasLink(to)) {
            a.cap.removeLink(to);
            b.cap.removeLink(from);
            PoolLedger.update(level, from, a.cap, a.te);
            PoolLedger.update(level, to, b.cap, b.te);
            a.te.setChanged();
            b.te.setChanged();
            level.playSound(null, to, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
            status(player, ChatFormatting.GRAY, "message.manaessencebridge.link_removed", to.getX(), to.getY(), to.getZ());
            return;
        }
        if (a.cap.getLinks().size() >= MAX_LINKS || b.cap.getLinks().size() >= MAX_LINKS) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.link_full", MAX_LINKS);
            return;
        }
        a.cap.addLink(to);
        b.cap.addLink(from);
        PoolLedger.update(level, from, a.cap, a.te);
        PoolLedger.update(level, to, b.cap, b.te);
        a.te.setChanged();
        b.te.setChanged();
        level.playSound(null, to, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
        EssenceTier tier = EssenceTier.byLevel(b.cap.getTier());
        if (tier != null) {
            PoolEffects.burst(level, to, tier, 20, 0.3);
        }
        status(player, ChatFormatting.AQUA, "message.manaessencebridge.link_added",
                to.getX(), to.getY(), to.getZ(), a.cap.getLinks().size());
        if (player instanceof ServerPlayer) {
            PoolThroughput.award((ServerPlayer) player, "pool_network");
        }
    }

    /** Связи пула - в чат. */
    static void describe(Level level, Player player, BlockPos pos) {
        Pool a = pool(level, pos);
        if (a == null) {
            return;
        }
        List<BlockPos> links = a.cap.getLinks();
        if (links.isEmpty()) {
            status(player, ChatFormatting.GRAY, "message.manaessencebridge.links_none");
            return;
        }
        player.sendSystemMessage(Component.translatable("message.manaessencebridge.links_header", links.size()).withStyle(ChatFormatting.AQUA));
        for (BlockPos p : links) {
            player.sendSystemMessage(Component.translatable("message.manaessencebridge.links_line", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Раз в секунду: каждая связанная пара загруженных пулов выравнивает заполненность. */
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        Level level = event.level;
        int rate = BridgeConfig.poolLinkRate();
        if (rate <= 0 || level.getGameTime() % 20 != 0) {
            return;
        }
        for (BlockPos pos : PoolAutoPull.tracked(level)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            Pool a = pool(level, pos);
            if (a == null || a.cap.getLinks().isEmpty()) {
                continue;
            }
            for (BlockPos other : new ArrayList<>(a.cap.getLinks())) {
                // каждую пару - один раз, со стороны пула с меньшей позицией
                if (other.asLong() < pos.asLong() || !level.isLoaded(other)) {
                    continue;
                }
                Pool b = pool(level, other);
                if (b == null || !b.cap.hasLink(pos)) {
                    a.cap.removeLink(other); // второго пула нет или он забыл связь
                    a.te.setChanged();
                    continue;
                }
                if (level.hasNeighborSignal(pos) || level.hasNeighborSignal(other)) {
                    continue;
                }
                balance(level, a, b, rate);
            }
        }
    }

    private static void balance(Level level, Pool a, Pool b, int rate) {
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
        from.te.setChanged();
        to.te.setChanged();
        EssenceTier tier = EssenceTier.byLevel(to.cap.getTier());
        if (tier != null) {
            PoolEffects.burst(level, to.te.getBlockPos(), tier, 3, 0.2);
        }
    }

    private static void status(Player player, ChatFormatting color, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }
}
