package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
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
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Мана-гроссбух: ПКМ открывает окно со всеми своими прокачанными пулами -
 * где стоят, тир, мана, оборот и автозабор (LedgerScreen). Свои - это где игрок поставил первый
 * катализатор. Не расходуется.
 */
public class ManaLedgerItem extends Item {

    public ManaLedgerItem() {
        super(new Item.Properties().stacksTo(1).tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MinecraftServer server = level.getServer();
        if (level.isClientSide || server == null) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        List<PoolLedger.Entry> pools = PoolLedger.listFor(server, player.getUUID());
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        PoolThroughput.award((ServerPlayer) player, "ledger_open");
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                LedgerPacket.of(pools, level.getGameTime()));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(text("tooltip.manaessencebridge.ledger_use", ChatFormatting.LIGHT_PURPLE));
        tooltip.add(text("tooltip.manaessencebridge.ledger_what", ChatFormatting.GRAY));
        tooltip.add(text("tooltip.manaessencebridge.crystal_reusable", ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent text(String key, ChatFormatting color, Object... args) {
        return Component.translatable(key, args).withStyle(color);
    }
}
