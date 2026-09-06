package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.spark.ISparkAttachable;

/**
 * Вся игровая логика мода:
 *
 * 1) ПКМ катализатором по Mana Pool -> поднимает пул на один тир (расходует предмет).
 * 2) ПКМ эссенцией по прокачанному пулу -> конвертирует ОДНУ эссенцию в ману.
 * 3) Shift+ПКМ эссенцией -> конвертирует ВЕСЬ стак (сколько влезет в пул).
 * 4) Shift+ПКМ пустой рукой -> выкупает эссенцию максимального тира,
 *    на который хватает маны.
 *
 * Курс обмена задан в EssenceTier - там же объяснено, почему он строго x4.
 */
public class CatalystInteractionHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Событие приходит на обе руки - работаем только по основной,
        // иначе одно нажатие обработалось бы дважды.
        if (event.getHand() != Hand.MAIN_HAND) {
            return;
        }

        World world = event.getWorld();
        BlockPos pos = event.getPos();
        PlayerEntity player = event.getPlayer();
        ItemStack heldItem = event.getItemStack();

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof IManaPool)) {
            return; // это не Mana Pool - нас это взаимодействие не касается
        }

        IManaPool pool = (IManaPool) te;
        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null) {
            return;
        }

        // Случай 1: применение катализатора
        if (heldItem.getItem() instanceof CatalystItem) {
            consume(event);
            if (!world.isRemote) {
                applyCatalyst(world, pos, player, heldItem, te, pool, cap,
                        ((CatalystItem) heldItem.getItem()).getTier());
            }
            return;
        }

        // Дальше всё только для уже прокачанных пулов
        if (!cap.isUpgraded()) {
            return;
        }

        // Случай 2 и 3: эссенция -> мана
        EssenceTier heldTier = EssenceTier.fromItem(heldItem.getItem());
        if (heldTier != null) {
            consume(event);
            if (!world.isRemote) {
                sellEssence(world, pos, player, heldItem, te, pool, cap, heldTier);
            }
            return;
        }

        // Случай 4: мана -> эссенция (Shift + пустая рука)
        if (heldItem.isEmpty() && player.isSneaking()) {
            consume(event);
            if (!world.isRemote) {
                buyEssence(world, pos, player, te, pool, cap);
            }
        }
    }

    // --- ветки логики -----------------------------------------------------

    private void applyCatalyst(World world, BlockPos pos, PlayerEntity player, ItemStack heldItem,
                               TileEntity te, IManaPool pool, InferiumCatalystCapability cap,
                               EssenceTier target) {
        int current = cap.getTier();

        if (!target.isEnabled()) {
            status(player, TextFormatting.RED, "message.manaessencebridge.tier_disabled",
                    target.getLevel(), target.getDisplayName());
            return;
        }

        ProgressGate gate = ProgressGate.forTier(target.getLevel());
        if (gate != null && player instanceof net.minecraft.entity.player.ServerPlayerEntity
                && !gate.isUnlockedFor((net.minecraft.entity.player.ServerPlayerEntity) player)) {
            status(player, TextFormatting.RED, "message.manaessencebridge.tier_locked",
                    target.getLevel(), target.getDisplayName(), gate.getRequirement());
            return;
        }

        if (current >= target.getLevel()) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.already_upgraded",
                    current, tierName(current));
            return;
        }

        if (current != target.getLevel() - 1) {
            int required = target.getLevel() - 1;
            status(player, TextFormatting.RED, "message.manaessencebridge.need_previous",
                    required, tierName(required));
            return;
        }

        cap.setTier(target.getLevel());
        pool.setColor(target.getPoolColor());
        syncPool(world, pos, te);
        // Капабилити не едут в стандартном пакете TileEntity - шлём тир сами,
        // иначе HUD у клиента не узнает о прокачке.
        ModNetwork.syncToTracking(world, pos, target.getLevel());

        if (!player.isCreative()) {
            heldItem.shrink(1);
        }

        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.BLOCKS, 1.0F, 1.0F + 0.1F * target.getLevel());
        status(player, TextFormatting.LIGHT_PURPLE, "message.manaessencebridge.upgraded",
                target.getLevel(), target.getDisplayName());
    }

    private void sellEssence(World world, BlockPos pos, PlayerEntity player, ItemStack heldItem,
                             TileEntity te, IManaPool pool, InferiumCatalystCapability cap,
                             EssenceTier tier) {
        if (!tier.isEnabled()) {
            status(player, TextFormatting.RED, "message.manaessencebridge.tier_disabled",
                    tier.getLevel(), tier.getDisplayName());
            return;
        }

        if (!cap.supports(tier)) {
            status(player, TextFormatting.RED, "message.manaessencebridge.tier_too_low",
                    cap.getTier(), tierName(cap.getTier()), tier.getDisplayName());
            return;
        }

        int manaPer = tier.getManaPerEssence();
        int space = availableSpace(te);

        if (space < manaPer) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.no_space",
                    CatalystItem.format(manaPer));
            return;
        }

        // Shift - весь стак, обычный клик - одна штука.
        int wanted = player.isSneaking() ? heldItem.getCount() : 1;
        int count = Math.min(wanted, space / manaPer);
        if (count <= 0) {
            return;
        }

        int mana = manaPer * count;
        pool.receiveMana(mana);
        syncPool(world, pos, te);

        if (!player.isCreative()) {
            heldItem.shrink(count);
        }

        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.BLOCKS, 0.6F, 1.6F);
        status(player, TextFormatting.AQUA, "message.manaessencebridge.sold",
                CatalystItem.format(mana), count, tier.getDisplayName());
    }

    private void buyEssence(World world, BlockPos pos, PlayerEntity player, TileEntity te,
                            IManaPool pool, InferiumCatalystCapability cap) {
        // По умолчанию выдаём инфериум: цена случайного нажатия мала, а разницы
        // в экономике нет - 4 инфериума стоят ровно столько же, сколько один
        // прудентиум, и крафт вверх в Mystical Agriculture бесплатный.
        // С buyHighestTier=true берём самый старший доступный тир - меньше кликов.
        EssenceTier best = null;
        if (BridgeConfig.buyHighestTier()) {
            for (int level = Math.min(cap.getTier(), BridgeConfig.maxTier()); level >= 1; level--) {
                EssenceTier candidate = EssenceTier.byLevel(level);
                if (candidate != null && pool.getCurrentMana() >= candidate.getManaCost()) {
                    best = candidate;
                    break;
                }
            }
        } else if (pool.getCurrentMana() >= EssenceTier.INFERIUM.getManaCost()) {
            best = EssenceTier.INFERIUM;
        }

        if (best == null) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.not_enough_mana",
                    CatalystItem.format(EssenceTier.INFERIUM.getManaCost()));
            return;
        }

        Item essence = ForgeRegistries.ITEMS.getValue(best.getEssenceId());
        ItemStack essenceStack = essence == null ? ItemStack.EMPTY : new ItemStack(essence);
        if (essenceStack.isEmpty()) {
            status(player, TextFormatting.RED, "message.manaessencebridge.essence_missing",
                    best.getEssenceId().toString());
            return;
        }

        pool.receiveMana(-best.getManaCost());
        syncPool(world, pos, te);

        if (!player.inventory.addItemStackToInventory(essenceStack)) {
            player.dropItem(essenceStack, false);
        }

        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.BLOCKS, 0.8F, 1.2F);
        status(player, TextFormatting.GOLD, "message.manaessencebridge.bought",
                CatalystItem.format(best.getManaCost()), best.getDisplayName());
    }

    // --- вспомогательное --------------------------------------------------

    /**
     * Свободное место в пуле. getAvailableSpaceForMana() лежит в публичном
     * API-интерфейсе ISparkAttachable, который Mana Pool реализует, - так что
     * во внутренности Botania лезть не приходится.
     */
    private static int availableSpace(TileEntity te) {
        if (te instanceof ISparkAttachable) {
            return Math.max(0, ((ISparkAttachable) te).getAvailableSpaceForMana());
        }
        return Integer.MAX_VALUE;
    }

    /** Сохраняем TileEntity и рассылаем его клиентам - иначе цвет пула не обновится. */
    private static void syncPool(World world, BlockPos pos, TileEntity te) {
        if (te == null) {
            return;
        }
        te.markDirty();
        BlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(ActionResultType.SUCCESS);
    }

    /** Название тира как компонент; для несуществующего тира - его номер. */
    private static Object tierName(int level) {
        EssenceTier tier = EssenceTier.byLevel(level);
        return tier == null ? String.valueOf(level) : tier.getDisplayName();
    }

    private static void status(PlayerEntity player, TextFormatting color, String key, Object... args) {
        IFormattableTextComponent message = new TranslationTextComponent(key, args);
        message.mergeStyle(color);
        player.sendStatusMessage(message, true);
    }
}
