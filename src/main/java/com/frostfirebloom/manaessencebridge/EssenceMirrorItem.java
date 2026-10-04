package com.frostfirebloom.manaessencebridge;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.api.mana.IManaPool;

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
    private static final int ACTION_COOLDOWN = 4;

    public EssenceMirrorItem() {
        super(new Item.Properties().maxStackSize(1).group(ItemGroup.MISC));
    }

    /** Привязать к пулу; вызывается из CatalystInteractionHandler. */
    static void bind(ItemStack stack, World world, BlockPos pos, PlayerEntity player) {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("dim", world.getDimensionKey().getLocation().toString());
        tag.putLong("pos", pos.toLong());
        stack.getOrCreateTag().put(TAG_POOL, tag);
        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, 1.2F);
        status(player, TextFormatting.AQUA, "message.manaessencebridge.mirror_bound",
                pos.getX(), pos.getY(), pos.getZ());
    }

    /** Привязанный пул, найденный и проверенный. */
    private static final class Bound {
        final ServerWorld world;
        final BlockPos pos;
        final TileEntity te;
        final IManaPool pool;
        final InferiumCatalystCapability cap;

        Bound(ServerWorld world, BlockPos pos, TileEntity te, InferiumCatalystCapability cap) {
            this.world = world;
            this.pos = pos;
            this.te = te;
            this.pool = (IManaPool) te;
            this.cap = cap;
        }
    }

    /** Найти пул зеркала; если его нет - сказать игроку почему и вернуть null. */
    @Nullable
    private static Bound resolve(ItemStack stack, ServerPlayerEntity player) {
        CompoundNBT bound = stack.getTag() == null ? null : stack.getTag().getCompound(TAG_POOL);
        if (bound == null || bound.isEmpty()) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.mirror_unbound");
            return null;
        }
        ServerWorld world = player.getServerWorld();
        if (!world.getDimensionKey().getLocation().toString().equals(bound.getString("dim"))) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.mirror_other_dim");
            return null;
        }
        BlockPos pos = BlockPos.fromLong(bound.getLong("pos"));
        // getTileEntity на сервере сам подгрузит чанк, если тот выгружен
        TileEntity te = world.getTileEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!(te instanceof IManaPool) || cap == null || !cap.isUpgraded()) {
            status(player, TextFormatting.RED, "message.manaessencebridge.mirror_pool_gone");
            return null;
        }
        return new Bound(world, pos, te, cap);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return ActionResult.func_233538_a_(stack, true);
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        Bound b = resolve(stack, sp);
        if (b == null) {
            return ActionResult.resultFail(stack);
        }
        player.getCooldownTracker().setCooldown(this, COOLDOWN);
        if (player.isSneaking()) {
            if (sendAll(b, player)) {
                done(sp, 1.4F);
            }
        } else {
            sendState(sp, hand, b, true);
            world.playSound(null, player.getPosition(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR,
                    SoundCategory.PLAYERS, 0.6F, 1.4F);
        }
        return ActionResult.resultSuccess(stack);
    }

    /** Кнопка окна; приходит в MirrorActionPacket. */
    static void handleAction(ServerPlayerEntity player, int handIndex, int action, int tierLevel) {
        Hand hand = handIndex == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof EssenceMirrorItem) || player.getCooldownTracker().hasCooldown(stack.getItem())) {
            return;
        }
        Bound b = resolve(stack, player);
        if (b == null) {
            return;
        }
        player.getCooldownTracker().setCooldown(stack.getItem(), ACTION_COOLDOWN);
        boolean ok;
        if (action == MirrorActionPacket.SEND_ALL) {
            ok = sendAll(b, player);
        } else {
            ok = buy(b, player, EssenceTier.byLevel(tierLevel), action == MirrorActionPacket.BUY_STACK ? 64 : 1);
        }
        if (ok) {
            done(player, action == MirrorActionPacket.SEND_ALL ? 1.4F : 1.0F);
        }
        sendState(player, hand, b, false);
    }

    private static void done(ServerPlayerEntity player, float pitch) {
        player.world.playSound(null, player.getPosition(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE,
                SoundCategory.PLAYERS, 0.8F, pitch);
        PoolThroughput.award(player, "essence_mirror");
    }

    /** Выкупить до count эссенций выбранного тира - сколько хватит маны. */
    private static boolean buy(Bound b, ServerPlayerEntity player, @Nullable EssenceTier tier, int count) {
        if (!PoolAccess.mayUse(player, b.cap.getOwner())) {
            status(player, TextFormatting.RED, "message.manaessencebridge.not_your_pool");
            return false;
        }
        if (tier == null || !tier.isEnabled() || !b.cap.supports(tier)) {
            return false;
        }
        int cost = tier.getManaCost();
        int n = Math.min(count, b.pool.getCurrentMana() / cost);
        if (n <= 0) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.not_enough_mana", CatalystItem.format(cost));
            return false;
        }
        Item essence = ForgeRegistries.ITEMS.getValue(tier.getEssenceId());
        if (essence == null || essence == Items.AIR) {
            status(player, TextFormatting.RED, "message.manaessencebridge.essence_missing", tier.getEssenceId().toString());
            return false;
        }
        long mana = (long) cost * n;
        b.pool.receiveMana((int) -mana);
        CatalystInteractionHandler.syncPool(b.world, b.pos, b.te);
        PoolThroughput.record(b.world, b.pos, b.cap, mana, player, false);
        ItemStack out = new ItemStack(essence, n);
        if (!player.inventory.addItemStackToInventory(out)) {
            player.dropItem(out, false);
        }
        status(player, TextFormatting.GOLD, "message.manaessencebridge.mirror_bought",
                n, tier.getDisplayName(), CatalystItem.format(mana));
        return true;
    }

    /** Вся эссенция из инвентаря - в пул, старшие тиры первыми, сколько влезет. */
    private static boolean sendAll(Bound b, PlayerEntity player) {
        long total = 0;
        int count = 0;
        boolean noSpace = false;
        List<ItemStack> items = player.inventory.mainInventory;
        for (int lvl = EssenceTier.maxEnabledLevel(); lvl >= 1; lvl--) {
            EssenceTier tier = EssenceTier.byLevel(lvl);
            if (tier == null || !tier.isEnabled() || !b.cap.supports(tier)) {
                continue;
            }
            int manaPer = tier.getManaPerEssence();
            for (ItemStack stack : items) {
                if (stack.isEmpty() || EssenceTier.fromItem(stack.getItem()) != tier) {
                    continue;
                }
                int space = Math.max(0, PoolCapacity.maxMana(b.te) - b.pool.getCurrentMana());
                int n = Math.min(stack.getCount(), space / manaPer);
                if (n <= 0) {
                    noSpace = true;
                    break;
                }
                int mana = manaPer * n;
                b.pool.receiveMana(mana);
                PoolThroughput.record(b.world, b.pos, b.cap, mana, player, true);
                stack.shrink(n);
                total += mana;
                count += n;
            }
        }
        if (count == 0) {
            status(player, TextFormatting.YELLOW, noSpace ? "message.manaessencebridge.mirror_pool_full"
                    : "message.manaessencebridge.mirror_nothing");
            return false;
        }
        CatalystInteractionHandler.syncPool(b.world, b.pos, b.te);
        status(player, TextFormatting.AQUA, "message.manaessencebridge.mirror_sent",
                count, CatalystItem.format(total));
        return true;
    }

    /** Отправить игроку состояние пула: открыть окно или обновить открытое. */
    private static void sendState(ServerPlayerEntity player, Hand hand, Bound b, boolean open) {
        List<MirrorPacket.Offer> offers = new ArrayList<>();
        for (int lvl = 1; lvl <= Math.min(b.cap.getTier(), EssenceTier.maxEnabledLevel()); lvl++) {
            EssenceTier tier = EssenceTier.byLevel(lvl);
            if (tier != null && tier.isEnabled()) {
                offers.add(new MirrorPacket.Offer(lvl, tier.getManaCost(), tier.getManaPerEssence()));
            }
        }
        MirrorPacket packet = new MirrorPacket(open, hand == Hand.OFF_HAND ? 1 : 0,
                b.world.getDimensionKey().getLocation().toString(), b.pos, b.cap.getTier(),
                b.pool.getCurrentMana(), PoolCapacity.maxMana(b.te), PoolAccess.mayUse(player, b.cap.getOwner()), offers);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        CompoundNBT bound = stack.getTag() == null ? null : stack.getTag().getCompound(TAG_POOL);
        if (bound == null || bound.isEmpty()) {
            tooltip.add(text("tooltip.manaessencebridge.mirror_bind", TextFormatting.YELLOW));
        } else {
            BlockPos pos = BlockPos.fromLong(bound.getLong("pos"));
            tooltip.add(text("tooltip.manaessencebridge.mirror_pool", TextFormatting.AQUA,
                    pos.getX(), pos.getY(), pos.getZ(), bound.getString("dim")));
        }
        tooltip.add(text("tooltip.manaessencebridge.mirror_use", TextFormatting.LIGHT_PURPLE));
        tooltip.add(text("tooltip.manaessencebridge.mirror_send", TextFormatting.LIGHT_PURPLE));
        tooltip.add(text("tooltip.manaessencebridge.mirror_dim", TextFormatting.DARK_GRAY));
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(TAG_POOL);
    }

    private static IFormattableTextComponent text(String key, TextFormatting color, Object... args) {
        return new TranslationTextComponent(key, args).mergeStyle(color);
    }

    private static void status(PlayerEntity player, TextFormatting color, String key, Object... args) {
        player.sendStatusMessage(text(key, color, args), true);
    }
}
