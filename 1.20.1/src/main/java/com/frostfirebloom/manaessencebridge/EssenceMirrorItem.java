package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.api.mana.ManaPool;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Зеркало эссенции: пул в кармане. ПКМ по прокачанному пулу привязывает
 * зеркало к нему, дальше в любом месте того же измерения ПКМ открывает окно
 * пула (MirrorScreen): мана, цены, выкуп любого тира по одной или стаком.
 * Shift+ПКМ сразу отправляет в пул всю эссенцию из инвентаря. Цены те же,
 * что у самого пула.
 *
 * Пул в незагруженном чанке на время действия подгружается - это действия
 * игрока, не автоматика, поэтому у каждого короткая перезарядка.
 */
public class EssenceMirrorItem extends Item {

    private static final String TAG_POOL = "BoundPool";
    private static final int COOLDOWN = 10;
    /** Перезарядка кнопок окна - чтобы зажатая мышь не грузила чанк пула каждый тик. */
    private static final int ACTION_COOLDOWN = 2;

    public EssenceMirrorItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** Привязать к пулу; вызывается из CatalystInteractionHandler. */
    static void bind(ItemStack stack, Level level, BlockPos pos, Player player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", level.dimension().location().toString());
        tag.putLong("pos", pos.asLong());
        stack.getOrCreateTag().put(TAG_POOL, tag);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        status(player, ChatFormatting.AQUA, "message.manaessencebridge.mirror_bound",
                pos.getX(), pos.getY(), pos.getZ());
    }

    /** Позиция привязанного пула, если он в этом измерении; иначе null. */
    @Nullable
    static BlockPos boundPos(ItemStack stack, Level level) {
        CompoundTag bound = stack.getTag() == null ? null : stack.getTag().getCompound(TAG_POOL);
        if (bound == null || bound.isEmpty()
                || !level.dimension().location().toString().equals(bound.getString("dim"))) {
            return null;
        }
        return BlockPos.of(bound.getLong("pos"));
    }

    /** Привязанный пул, найденный и проверенный. */
    private static final class Bound {
        final ServerLevel level;
        final BlockPos pos;
        final BlockEntity te;
        final ManaPool pool;
        final InferiumCatalystCapability cap;

        Bound(ServerLevel level, BlockPos pos, BlockEntity te, InferiumCatalystCapability cap) {
            this.level = level;
            this.pos = pos;
            this.te = te;
            this.pool = (ManaPool) te;
            this.cap = cap;
        }
    }

    /** Найти пул зеркала; если его нет - сказать игроку почему и вернуть null. */
    @Nullable
    private static Bound resolve(ItemStack stack, ServerPlayer player) {
        CompoundTag bound = stack.getTag() == null ? null : stack.getTag().getCompound(TAG_POOL);
        if (bound == null || bound.isEmpty()) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.mirror_unbound");
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().toString().equals(bound.getString("dim"))) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.mirror_other_dim");
            return null;
        }
        BlockPos pos = BlockPos.of(bound.getLong("pos"));
        // getBlockEntity у ServerLevel сам подгрузит чанк, если тот выгружен
        BlockEntity te = level.getBlockEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!(te instanceof ManaPool) || cap == null || !cap.isUpgraded()) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.mirror_pool_gone");
            return null;
        }
        return new Bound(level, pos, te, cap);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        ServerPlayer sp = (ServerPlayer) player;
        // перезарядка до поиска пула: даже неудачный клик не грузит чанк снова и снова;
        // окно - коротко, чтобы первая кнопка в нём сразу работала
        player.getCooldowns().addCooldown(this, player.isShiftKeyDown() ? COOLDOWN : ACTION_COOLDOWN);
        Bound b = resolve(stack, sp);
        if (b == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            if (send(b, player, 0, Integer.MAX_VALUE)) {
                done(sp, 1.4F);
            }
        } else {
            sendState(sp, hand, b, true);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        return InteractionResultHolder.success(stack);
    }

    /** Кнопка окна; приходит в MirrorActionPacket. */
    static void handleAction(ServerPlayer player, int handIndex, int action, int tierLevel, int count) {
        InteractionHand hand = handIndex == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof EssenceMirrorItem) || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        player.getCooldowns().addCooldown(stack.getItem(), ACTION_COOLDOWN);
        Bound b = resolve(stack, player);
        if (b == null) {
            return;
        }
        boolean ok;
        if (action == MirrorActionPacket.SEND_ALL) {
            ok = send(b, player, 0, Integer.MAX_VALUE);
        } else if (action == MirrorActionPacket.SELL) {
            ok = send(b, player, tierLevel, Math.max(1, Math.min(count, 64 * 36)));
        } else {
            ok = buy(b, player, EssenceTier.byLevel(tierLevel), Math.max(1, Math.min(count, 64)));
        }
        if (ok) {
            done(player, action == MirrorActionPacket.SEND_ALL ? 1.4F : 1.0F);
        }
        sendState(player, hand, b, false);
    }

    private static void done(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.PLAYERS, 0.8F, pitch);
        PoolThroughput.award(player, "essence_mirror");
    }

    /** Выкупить до count эссенций выбранного тира - сколько хватит маны. */
    private static boolean buy(Bound b, ServerPlayer player, @Nullable EssenceTier tier, int count) {
        if (!PoolAccess.mayUse(player, b.cap.getOwner())) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.not_your_pool");
            return false;
        }
        if (tier == null || !tier.isEnabled() || !b.cap.supports(tier)) {
            return false;
        }
        int cost = tier.getManaCost();
        int n = Math.min(count, b.pool.getCurrentMana() / cost);
        if (n <= 0) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.not_enough_mana", CatalystItem.format(cost));
            return false;
        }
        Item essence = ForgeRegistries.ITEMS.getValue(tier.getEssenceId());
        if (essence == null || essence == net.minecraft.world.item.Items.AIR) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.essence_missing", tier.getEssenceId().toString());
            return false;
        }
        long mana = (long) cost * n;
        b.pool.receiveMana((int) -mana);
        sync(b);
        PoolThroughput.record(b.level, b.pos, b.cap, mana, player, false);
        ItemStack out = new ItemStack(essence, n);
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        status(player, ChatFormatting.GOLD, "message.manaessencebridge.mirror_bought",
                n, tier.getDisplayName(), CatalystItem.format(mana));
        return true;
    }

    /**
     * Эссенция из инвентаря - в пул, старшие тиры первыми, сколько влезет:
     * вся (onlyTier = 0) или только одного тира, не больше limit штук.
     */
    private static boolean send(Bound b, Player player, int onlyTier, int limit) {
        long total = 0;
        int count = 0;
        boolean noSpace = false;
        List<ItemStack> items = player.getInventory().items;
        for (int lvl = EssenceTier.maxEnabledLevel(); lvl >= 1; lvl--) {
            EssenceTier tier = EssenceTier.byLevel(lvl);
            if (count >= limit) {
                break;
            }
            if (tier == null || !tier.isEnabled() || !b.cap.supports(tier) || (onlyTier > 0 && lvl != onlyTier)) {
                continue;
            }
            int manaPer = tier.getManaPerEssence();
            for (ItemStack stack : items) {
                if (stack.isEmpty() || EssenceTier.fromItem(stack.getItem()) != tier) {
                    continue;
                }
                int space = Math.max(0, b.pool.getMaxMana() - b.pool.getCurrentMana());
                int n = Math.min(Math.min(stack.getCount(), limit - count), space / manaPer);
                if (n <= 0) {
                    noSpace = true;
                    break;
                }
                int mana = manaPer * n;
                b.pool.receiveMana(mana);
                PoolThroughput.record(b.level, b.pos, b.cap, mana, player, true);
                stack.shrink(n);
                total += mana;
                count += n;
                if (count >= limit) {
                    break;
                }
            }
        }
        // предметы из курсов датапака - только при «отправить всё»
        for (ItemStack stack : onlyTier > 0 ? java.util.Collections.<ItemStack>emptyList() : items) {
            PoolExchange.Price price = PoolExchange.custom(stack);
            if (count >= limit || price == null || !price.fits(b.cap)) {
                continue;
            }
            int space = Math.max(0, b.pool.getMaxMana() - b.pool.getCurrentMana());
            int n = Math.min(Math.min(stack.getCount(), limit - count), space / price.mana);
            if (n <= 0) {
                noSpace = true;
                continue;
            }
            int mana = price.mana * n;
            b.pool.receiveMana(mana);
            PoolThroughput.record(b.level, b.pos, b.cap, mana, player, true);
            stack.shrink(n);
            total += mana;
            count += n;
        }
        if (count == 0) {
            status(player, ChatFormatting.YELLOW, noSpace ? "message.manaessencebridge.mirror_pool_full"
                    : "message.manaessencebridge.mirror_nothing");
            return false;
        }
        sync(b);
        status(player, ChatFormatting.AQUA, "message.manaessencebridge.mirror_sent",
                count, CatalystItem.format(total));
        return true;
    }

    private static void sync(Bound b) {
        b.te.setChanged();
        b.level.sendBlockUpdated(b.pos, b.level.getBlockState(b.pos), b.level.getBlockState(b.pos), 3);
    }

    /** Отправить игроку состояние пула: открыть окно или обновить открытое. */
    private static void sendState(ServerPlayer player, InteractionHand hand, Bound b, boolean open) {
        List<MirrorPacket.Offer> offers = new ArrayList<>();
        for (int lvl = 1; lvl <= Math.min(b.cap.getTier(), EssenceTier.maxEnabledLevel()); lvl++) {
            EssenceTier tier = EssenceTier.byLevel(lvl);
            if (tier != null && tier.isEnabled()) {
                offers.add(new MirrorPacket.Offer(lvl, tier.getManaCost(), tier.getManaPerEssence()));
            }
        }
        MirrorPacket packet = new MirrorPacket(open, hand == InteractionHand.OFF_HAND ? 1 : 0,
                b.level.dimension().location().toString(), b.pos, b.cap.getTier(),
                b.pool.getCurrentMana(), b.pool.getMaxMana(), PoolAccess.mayUse(player, b.cap.getOwner()), b.cap.getLinks().size(), offers);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag bound = stack.getTag() == null ? null : stack.getTag().getCompound(TAG_POOL);
        if (bound == null || bound.isEmpty()) {
            tooltip.add(text("tooltip.manaessencebridge.mirror_bind", ChatFormatting.YELLOW));
        } else {
            BlockPos pos = BlockPos.of(bound.getLong("pos"));
            tooltip.add(text("tooltip.manaessencebridge.mirror_pool", ChatFormatting.AQUA,
                    pos.getX(), pos.getY(), pos.getZ(), bound.getString("dim")));
        }
        tooltip.add(text("tooltip.manaessencebridge.mirror_use", ChatFormatting.LIGHT_PURPLE));
        tooltip.add(text("tooltip.manaessencebridge.mirror_send", ChatFormatting.LIGHT_PURPLE));
        tooltip.add(text("tooltip.manaessencebridge.mirror_link", ChatFormatting.LIGHT_PURPLE));
        tooltip.add(text("tooltip.manaessencebridge.mirror_dim", ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(TAG_POOL);
    }

    private static MutableComponent text(String key, ChatFormatting color, Object... args) {
        return Component.translatable(key, args).withStyle(color);
    }

    private static void status(Player player, ChatFormatting color, String key, Object... args) {
        player.displayClientMessage(text(key, color, args), true);
    }
}
