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
import vazkii.botania.api.mana.IThrottledPacket;
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
            if (!world.isRemote && mayAct(player, cap)) {
                applyCatalyst(world, pos, player, heldItem, te, pool, cap,
                        ((CatalystItem) heldItem.getItem()).getTier());
            }
            return;
        }


        // Случай 1б: резонансный кристалл снимает верхний тир.
        // Требуем приседание: действие необратимое, случайный клик по пулу
        // не должен разбирать прокачку.
        if (heldItem.getItem() instanceof ResonanceCrystalItem) {
            if (!player.isSneaking()) {
                return;
            }
            consume(event);
            if (!world.isRemote && mayAct(player, cap)) {
                removeTier(world, pos, player, te, pool, cap);
            }
            return;
        }

        // Случай 1в: сифон включает и выключает автозабор из сундуков.
        if (heldItem.getItem() instanceof EssenceSiphonItem) {
            consume(event);
            if (!world.isRemote && mayAct(player, cap)) {
                toggleAutoPull(world, pos, player, te, cap);
            }
            return;
        }

        // Случай 1г: зеркало эссенции привязывается к прокачанному пулу.
        if (heldItem.getItem() instanceof EssenceMirrorItem) {
            consume(event);
            if (!world.isRemote) {
                BlockPos bound = EssenceMirrorItem.boundPos(heldItem, world);
                if (player.isSneaking() && bound != null) {
                    // сеть пулов: связать этот пул с пулом зеркала (или показать связи)
                    PoolNetwork.toggle(world, player, bound, pos);
                } else if (!cap.isUpgraded()) {
                    status(player, TextFormatting.YELLOW, "message.manaessencebridge.not_upgraded");
                } else if (mayAct(player, cap)) {
                    EssenceMirrorItem.bind(heldItem, world, pos, player);
                }
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

        // Случай 2б: предмет из курсов датапака (PoolExchange) -> мана
        PoolExchange.Price custom = PoolExchange.custom(heldItem);
        if (custom != null) {
            consume(event);
            if (!world.isRemote) {
                sellCustom(world, pos, player, heldItem, te, pool, cap, custom);
            }
            return;
        }

        // Случай 4: мана -> эссенция (Shift + пустая рука)
        if (heldItem.isEmpty() && player.isSneaking()) {
            consume(event);
            if (!world.isRemote && mayAct(player, cap)) {
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

        // Пул должен вмещать хотя бы одну эссенцию нового тира, иначе
        // катализатор потратится впустую: ни продать, ни выкупить её
        // в таком пуле не выйдет. Касается прежде всего разбавленного пула.
        int targetCapacity = PoolCapacity.capacityFor(cap, te, target.getLevel());
        if (targetCapacity < target.getManaCost()) {
            status(player, TextFormatting.RED, "message.manaessencebridge.pool_too_small",
                    target.getLevel(), CatalystItem.format(targetCapacity),
                    CatalystItem.format(target.getManaCost()));
            return;
        }

        cap.setTier(target.getLevel());
        if (cap.getOwner() == null) {
            // Первый катализатор назначает хозяина - ему пойдут достижения
            // за оборот, накрученный автоматикой.
            cap.setOwner(player.getUniqueID());
        }
        pool.setColor(target.getPoolColor());
        PoolCapacity.apply(world, pos, te, cap);
        syncPool(world, pos, te);
        // Капабилити не едут в стандартном пакете TileEntity - шлём тир сами,
        // иначе HUD у клиента не узнает о прокачке.
        ModNetwork.syncToTracking(world, pos, cap);
        PoolAutoPull.track(world, pos);
        PoolThroughput.onTierChanged(player, cap);

        if (!player.isCreative()) {
            heldItem.shrink(1);
        }

        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.BLOCKS, 1.0F, 1.0F + 0.1F * target.getLevel());
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.BLOCKS, 0.7F, 0.8F + 0.15F * target.getLevel());
        PoolEffects.burst(world, pos, target, 60, 0.45);
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
        PoolThroughput.record(world, pos, cap, mana, player, true);

        if (!player.isCreative()) {
            heldItem.shrink(count);
        }

        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.BLOCKS, 0.6F, 1.6F);
        PoolEffects.burst(world, pos, tier, Math.min(8 + count * 2, 40), 0.3);
        status(player, TextFormatting.AQUA, "message.manaessencebridge.sold",
                CatalystItem.format(mana), count, tier.getDisplayName());
    }

    /** Предмет из курсов датапака: как эссенция, только цена и тир - из записи. */
    private void sellCustom(World world, BlockPos pos, PlayerEntity player, ItemStack heldItem,
                            TileEntity te, IManaPool pool, InferiumCatalystCapability cap, PoolExchange.Price price) {
        if (!price.fits(cap)) {
            status(player, TextFormatting.RED, "message.manaessencebridge.tier_too_low",
                    cap.getTier(), tierName(cap.getTier()), price.look().getDisplayName());
            return;
        }
        int space = availableSpace(te);
        if (space < price.mana) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.no_space", CatalystItem.format(price.mana));
            return;
        }
        int wanted = player.isSneaking() ? heldItem.getCount() : 1;
        int count = Math.min(wanted, space / price.mana);
        if (count <= 0) {
            return;
        }
        int mana = price.mana * count;
        net.minecraft.util.text.ITextComponent name = heldItem.getDisplayName();
        pool.receiveMana(mana);
        syncPool(world, pos, te);
        PoolThroughput.record(world, pos, cap, mana, player, true);
        if (!player.isCreative()) {
            heldItem.shrink(count);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.6F, 1.6F);
        PoolEffects.burst(world, pos, price.look(), Math.min(8 + count * 2, 40), 0.3);
        status(player, TextFormatting.AQUA, "message.manaessencebridge.sold", CatalystItem.format(mana), count, name);
    }

    /** Выкуп эссенции за ману пула; true - выкупили. */
    static boolean buyEssence(World world, BlockPos pos, PlayerEntity player, TileEntity te,
                              IManaPool pool, InferiumCatalystCapability cap) {
        // По умолчанию выдаём инфериум: цена случайного нажатия мала, а разницы
        // в экономике нет - 4 инфериума стоят ровно столько же, сколько один
        // прудентиум, и крафт вверх в Mystical Agriculture бесплатный.
        // С buyHighestTier=true берём самый старший доступный тир - меньше кликов.
        EssenceTier best = null;
        if (BridgeConfig.buyHighestTier()) {
            for (int level = Math.min(cap.getTier(), EssenceTier.maxEnabledLevel()); level >= 1; level--) {
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
            return false;
        }

        Item essence = ForgeRegistries.ITEMS.getValue(best.getEssenceId());
        ItemStack essenceStack = essence == null ? ItemStack.EMPTY : new ItemStack(essence);
        if (essenceStack.isEmpty()) {
            status(player, TextFormatting.RED, "message.manaessencebridge.essence_missing",
                    best.getEssenceId().toString());
            return false;
        }

        pool.receiveMana(-best.getManaCost());
        syncPool(world, pos, te);
        PoolThroughput.record(world, pos, cap, best.getManaCost(), player, false);

        if (!player.inventory.addItemStackToInventory(essenceStack)) {
            player.dropItem(essenceStack, false);
        }

        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.BLOCKS, 0.8F, 1.2F);
        PoolEffects.burst(world, pos, best, 12, 0.3);
        status(player, TextFormatting.GOLD, "message.manaessencebridge.bought",
                CatalystItem.format(best.getManaCost()), best.getDisplayName());
        return true;
    }




    /**
     * Сифон: включает или выключает автозабор эссенции из соседних контейнеров.
     * Сигнал редстоуна на пуле работает как замок у воронки - ставит на паузу,
     * но сам переключатель не трогает.
     */
    private void toggleAutoPull(World world, BlockPos pos, PlayerEntity player, TileEntity te,
                                InferiumCatalystCapability cap) {
        if (!cap.isUpgraded()) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.not_upgraded");
            return;
        }
        if (!BridgeConfig.pullFromContainers()) {
            status(player, TextFormatting.RED, "message.manaessencebridge.pull_server_off");
            return;
        }

        boolean enabled = !cap.isPullEnabled();
        cap.setPullEnabled(enabled);
        te.markDirty();
        ModNetwork.syncToTracking(world, pos, cap);
        if (enabled) {
            PoolAutoPull.track(world, pos);
            if (player instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                PoolThroughput.award((net.minecraft.entity.player.ServerPlayerEntity) player, "siphon_on");
            }
        }

        world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.5F, enabled ? 0.9F : 0.6F);
        if (!enabled) {
            status(player, TextFormatting.GRAY, "message.manaessencebridge.pull_off");
        } else if (world.isBlockPowered(pos)) {
            status(player, TextFormatting.GOLD, "message.manaessencebridge.pull_on_powered");
        } else {
            status(player, TextFormatting.AQUA, "message.manaessencebridge.pull_on");
        }
    }

    /**
     * Снимает верхний тир: возвращает катализатор, списывает ману из пула
     * и откатывает цвет к предыдущему тиру (или к белому у чистого пула).
     */
    private void removeTier(World world, BlockPos pos, PlayerEntity player, TileEntity te,
                            IManaPool pool, InferiumCatalystCapability cap) {
        EssenceTier current = EssenceTier.byLevel(cap.getTier());
        if (current == null) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.not_upgraded");
            return;
        }

        int cost = current.getManaCost();
        if (pool.getCurrentMana() < cost) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.crystal_no_mana",
                    CatalystItem.format(cost));
            return;
        }

        // Ёмкость после снятия тира уменьшится. Если мана в неё не влезет,
        // лишнее сгорело бы - поэтому отказываем и просим сначала потратить.
        int reducedCapacity = PoolCapacity.capacityFor(cap, te, Math.max(current.getLevel() - 1, 1));
        if (pool.getCurrentMana() - cost > reducedCapacity) {
            status(player, TextFormatting.YELLOW, "message.manaessencebridge.crystal_overflow",
                    CatalystItem.format(reducedCapacity));
            return;
        }

        pool.receiveMana(-cost);
        cap.setTier(current.getLevel() - 1);
        if (!cap.isUpgraded()) {
            // Чистый пул не помнит автозабор: иначе после новой прокачки
            // он внезапно начал бы тянуть из сундуков сам.
            cap.setPullEnabled(false);
        }

        EssenceTier below = EssenceTier.byLevel(cap.getTier());
        pool.setColor(below == null ? net.minecraft.item.DyeColor.WHITE : below.getPoolColor());
        PoolCapacity.apply(world, pos, te, cap);
        syncPool(world, pos, te);
        ModNetwork.syncToTracking(world, pos, cap);
        if (!cap.isUpgraded()) {
            PoolAutoPull.untrack(world, pos);
        }

        ItemStack refund = new ItemStack(ModItems.getCatalyst(current).get());
        if (!player.inventory.addItemStackToInventory(refund)) {
            player.dropItem(refund, false);
        }

        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE,
                SoundCategory.BLOCKS, 0.8F, 1.3F);
        PoolEffects.burst(world, pos, current, 30, 0.4);
        status(player, TextFormatting.LIGHT_PURPLE, "message.manaessencebridge.tier_removed",
                current.getLevel(), current.getDisplayName(), CatalystItem.format(cost));
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
    static void syncPool(World world, BlockPos pos, TileEntity te) {
        if (te == null) {
            return;
        }
        te.markDirty();
        BlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);

        // У пула Botania своя очередь пакетов: без этого вызова клиент
        // не узнает о смене цвета, пока пул не синхронизируется по другой
        // причине - например, из-за изменения маны.
        if (te instanceof IThrottledPacket) {
            ((IThrottledPacket) te).markDispatchable();
        }
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

    /** Приватные пулы: чужим пулом распоряжается только хозяин (и операторы). */
    private static boolean mayAct(PlayerEntity player, InferiumCatalystCapability cap) {
        if (PoolAccess.mayUse(player, cap.getOwner())) {
            return true;
        }
        status(player, TextFormatting.RED, "message.manaessencebridge.not_your_pool");
        return false;
    }

    private static void status(PlayerEntity player, TextFormatting color, String key, Object... args) {
        IFormattableTextComponent message = new TranslationTextComponent(key, args);
        message.mergeStyle(color);
        player.sendStatusMessage(message, true);
    }
}
