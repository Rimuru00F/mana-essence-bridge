package com.frostfirebloom.manaessencebridge;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Мана-гроссбух: ПКМ открывает окно со всеми своими прокачанными пулами -
 * где стоят, тир, мана, оборот и автозабор (LedgerScreen). Свои - это где игрок поставил первый
 * катализатор. Не расходуется.
 */
public class ManaLedgerItem extends Item {

    public ManaLedgerItem() {
        super(new Item.Properties().maxStackSize(1).group(ItemGroup.MISC));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getHeldItem(hand);
        MinecraftServer server = world.getServer();
        if (world.isRemote || server == null) {
            return ActionResult.func_233538_a_(stack, world.isRemote);
        }
        List<PoolLedger.Entry> pools = PoolLedger.listFor(server, player.getUniqueID());
        world.playSound(null, player.getPosition(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8F, 1.0F);
        PoolThroughput.award((ServerPlayerEntity) player, "ledger_open");
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
                LedgerPacket.of(pools, world.getGameTime()));
        return ActionResult.resultSuccess(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(text("tooltip.manaessencebridge.ledger_use", TextFormatting.LIGHT_PURPLE));
        tooltip.add(text("tooltip.manaessencebridge.ledger_what", TextFormatting.GRAY));
        tooltip.add(text("tooltip.manaessencebridge.crystal_reusable", TextFormatting.DARK_GRAY));
    }

    private static void send(PlayerEntity player, ITextComponent message) {
        player.sendMessage(message, Util.DUMMY_UUID);
    }

    private static IFormattableTextComponent text(String key, TextFormatting color, Object... args) {
        return new TranslationTextComponent(key, args).mergeStyle(color);
    }
}
