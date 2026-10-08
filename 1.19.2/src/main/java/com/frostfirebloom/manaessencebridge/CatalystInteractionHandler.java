package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.api.mana.ManaPool;

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
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Level world = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        ItemStack heldItem = event.getItemStack();

        BlockEntity te = world.getBlockEntity(pos);
        if (!(te instanceof ManaPool)) {
            return; // это не Mana Pool - нас это взаимодействие не касается
        }

        ManaPool pool = (ManaPool) te;
        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null) {
            return;
        }

        // Случай 1: применение катализатора
        if (heldItem.getItem() instanceof CatalystItem) {
            consume(event);
            if (!world.isClientSide && mayAct(player, cap)) {
                applyCatalyst(world, pos, player, heldItem, te, pool, cap,
                        ((CatalystItem) heldItem.getItem()).getTier());
            }
            return;
        }


        // Случай 1б: резонансный кристалл снимает верхний тир.
        // Требуем приседание: действие необратимое, случайный клик по пулу
        // не должен разбирать прокачку.
        if (heldItem.getItem() instanceof ResonanceCrystalItem) {
            if (!player.isShiftKeyDown()) {
                return;
            }
            consume(event);
            if (!world.isClientSide && mayAct(player, cap)) {
                removeTier(world, pos, player, te, pool, cap);
            }
            return;
        }

        // Случай 1в: сифон включает и выключает автозабор из сундуков.
        if (heldItem.getItem() instanceof EssenceSiphonItem) {
            consume(event);
            if (!world.isClientSide && mayAct(player, cap)) {
                toggleAutoPull(world, pos, player, te, cap);
            }
            return;
        }

        // Случай 1г: зеркало эссенции привязывается к прокачанному пулу.
        if (heldItem.getItem() instanceof EssenceMirrorItem) {
            consume(event);
            if (!world.isClientSide) {
                BlockPos bound = EssenceMirrorItem.boundPos(heldItem, world);
                if (player.isShiftKeyDown() && bound != null) {
                    // сеть пулов: связать этот пул с пулом зеркала (или показать связи)
                    PoolNetwork.toggle(world, player, bound, pos);
                } else if (!cap.isUpgraded()) {
                    status(player, ChatFormatting.YELLOW, "message.manaessencebridge.not_upgraded");
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
            if (!world.isClientSide) {
                sellEssence(world, pos, player, heldItem, te, pool, cap, heldTier);
            }
            return;
        }

        // Случай 2б: предмет из курсов датапака (PoolExchange) -> мана
        PoolExchange.Price custom = PoolExchange.custom(heldItem);
        if (custom != null) {
            consume(event);
            if (!world.isClientSide) {
                sellCustom(world, pos, player, heldItem, te, pool, cap, custom);
            }
            return;
        }

        // Случай 4: мана -> эссенция (Shift + пустая рука)
        if (heldItem.isEmpty() && player.isShiftKeyDown()) {
            consume(event);
            if (!world.isClientSide && mayAct(player, cap)) {
                buyEssence(world, pos, player, te, pool, cap);
            }
        }
    }

    // --- ветки логики -----------------------------------------------------

    private void applyCatalyst(Level world, BlockPos pos, Player player, ItemStack heldItem,
                               BlockEntity te, ManaPool pool, InferiumCatalystCapability cap,
                               EssenceTier target) {
        int current = cap.getTier();

        if (!target.isEnabled()) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.tier_disabled",
                    target.getLevel(), target.getDisplayName());
            return;
        }

        ProgressGate gate = ProgressGate.forTier(target.getLevel());
        if (gate != null && player instanceof net.minecraft.server.level.ServerPlayer
                && !gate.isUnlockedFor((net.minecraft.server.level.ServerPlayer) player)) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.tier_locked",
                    target.getLevel(), target.getDisplayName(), gate.getRequirement());
            return;
        }

        if (current >= target.getLevel()) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.already_upgraded",
                    current, tierName(current));
            return;
        }

        if (current != target.getLevel() - 1) {
            int required = target.getLevel() - 1;
            status(player, ChatFormatting.RED, "message.manaessencebridge.need_previous",
                    required, tierName(required));
            return;
        }

        // Пул должен вмещать хотя бы одну эссенцию нового тира, иначе
        // катализатор потратится впустую: ни продать, ни выкупить её
        // в таком пуле не выйдет. Касается прежде всего разбавленного пула.
        int targetCapacity = PoolCapacity.capacityFor(cap, te, target.getLevel());
        if (targetCapacity < target.getManaCost()) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.pool_too_small",
                    target.getLevel(), CatalystItem.format(targetCapacity),
                    CatalystItem.format(target.getManaCost()));
            return;
        }

        cap.setTier(target.getLevel());
        if (cap.getOwner() == null) {
            // Первый катализатор назначает хозяина - ему пойдут достижения
            // за оборот, накрученный автоматикой.
            cap.setOwner(player.getUUID());
        }
        pool.setColor(java.util.Optional.of(target.getPoolColor()));
        PoolCapacity.apply(world, pos, te, cap);
        syncPool(world, pos, te);
        // Капабилити не едут в стандартном пакете BlockEntity - шлём тир сами,
        // иначе HUD у клиента не узнает о прокачке.
        ModNetwork.syncToTracking(world, pos, cap);
        PoolAutoPull.track(world, pos);
        PoolThroughput.onTierChanged(player, cap);

        if (!player.isCreative()) {
            heldItem.shrink(1);
        }

        world.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.BLOCKS, 1.0F, 1.0F + 0.1F * target.getLevel());
        world.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT,
                SoundSource.BLOCKS, 0.7F, 0.8F + 0.15F * target.getLevel());
        PoolEffects.burst(world, pos, target, 60, 0.45);
        status(player, ChatFormatting.LIGHT_PURPLE, "message.manaessencebridge.upgraded",
                target.getLevel(), target.getDisplayName());
    }

    private void sellEssence(Level world, BlockPos pos, Player player, ItemStack heldItem,
                             BlockEntity te, ManaPool pool, InferiumCatalystCapability cap,
                             EssenceTier tier) {
        if (!tier.isEnabled()) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.tier_disabled",
                    tier.getLevel(), tier.getDisplayName());
            return;
        }

        if (!cap.supports(tier)) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.tier_too_low",
                    cap.getTier(), tierName(cap.getTier()), tier.getDisplayName());
            return;
        }

        int manaPer = tier.getManaPerEssence();
        int space = availableSpace(te);

        if (space < manaPer) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.no_space",
                    CatalystItem.format(manaPer));
            return;
        }

        // Shift - весь стак, обычный клик - одна штука.
        int wanted = player.isShiftKeyDown() ? heldItem.getCount() : 1;
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

        world.playSound(null, pos, SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 0.6F, 1.6F);
        PoolEffects.burst(world, pos, tier, Math.min(8 + count * 2, 40), 0.3);
        status(player, ChatFormatting.AQUA, "message.manaessencebridge.sold",
                CatalystItem.format(mana), count, tier.getDisplayName());
    }

    /** Предмет из курсов датапака: как эссенция, только цена и тир - из записи. */
    private void sellCustom(Level world, BlockPos pos, Player player, ItemStack heldItem,
                            BlockEntity te, ManaPool pool, InferiumCatalystCapability cap, PoolExchange.Price price) {
        if (!price.fits(cap)) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.tier_too_low",
                    cap.getTier(), tierName(cap.getTier()), price.look().getDisplayName());
            return;
        }
        int space = availableSpace(te);
        if (space < price.mana) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.no_space", CatalystItem.format(price.mana));
            return;
        }
        int wanted = player.isShiftKeyDown() ? heldItem.getCount() : 1;
        int count = Math.min(wanted, space / price.mana);
        if (count <= 0) {
            return;
        }
        int mana = price.mana * count;
        Component name = heldItem.getHoverName();
        pool.receiveMana(mana);
        syncPool(world, pos, te);
        PoolThroughput.record(world, pos, cap, mana, player, true);
        if (!player.isCreative()) {
            heldItem.shrink(count);
        }
        world.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
        PoolEffects.burst(world, pos, price.look(), Math.min(8 + count * 2, 40), 0.3);
        status(player, ChatFormatting.AQUA, "message.manaessencebridge.sold", CatalystItem.format(mana), count, name);
    }

    /** Выкуп эссенции за ману пула; true - выкупили. */
    static boolean buyEssence(Level world, BlockPos pos, Player player, BlockEntity te,
                              ManaPool pool, InferiumCatalystCapability cap) {
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
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.not_enough_mana",
                    CatalystItem.format(EssenceTier.INFERIUM.getManaCost()));
            return false;
        }

        Item essence = ForgeRegistries.ITEMS.getValue(best.getEssenceId());
        ItemStack essenceStack = essence == null ? ItemStack.EMPTY : new ItemStack(essence);
        if (essenceStack.isEmpty()) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.essence_missing",
                    best.getEssenceId().toString());
            return false;
        }

        pool.receiveMana(-best.getManaCost());
        syncPool(world, pos, te);
        PoolThroughput.record(world, pos, cap, best.getManaCost(), player, false);

        if (!player.getInventory().add(essenceStack)) {
            player.drop(essenceStack, false);
        }

        world.playSound(null, pos, SoundEvents.ITEM_PICKUP,
                SoundSource.BLOCKS, 0.8F, 1.2F);
        PoolEffects.burst(world, pos, best, 12, 0.3);
        status(player, ChatFormatting.GOLD, "message.manaessencebridge.bought",
                CatalystItem.format(best.getManaCost()), best.getDisplayName());
        return true;
    }




    /**
     * Сифон: включает или выключает автозабор эссенции из соседних контейнеров.
     * Сигнал редстоуна на пуле работает как замок у воронки - ставит на паузу,
     * но сам переключатель не трогает.
     */
    private void toggleAutoPull(Level world, BlockPos pos, Player player, BlockEntity te,
                                InferiumCatalystCapability cap) {
        if (!cap.isUpgraded()) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.not_upgraded");
            return;
        }
        if (!BridgeConfig.pullFromContainers()) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.pull_server_off");
            return;
        }

        boolean enabled = !cap.isPullEnabled();
        cap.setPullEnabled(enabled);
        te.setChanged();
        ModNetwork.syncToTracking(world, pos, cap);
        if (enabled) {
            PoolAutoPull.track(world, pos);
            if (player instanceof net.minecraft.server.level.ServerPlayer) {
                PoolThroughput.award((net.minecraft.server.level.ServerPlayer) player, "siphon_on");
            }
        }

        world.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, enabled ? 0.9F : 0.6F);
        if (!enabled) {
            status(player, ChatFormatting.GRAY, "message.manaessencebridge.pull_off");
        } else if (world.hasNeighborSignal(pos)) {
            status(player, ChatFormatting.GOLD, "message.manaessencebridge.pull_on_powered");
        } else {
            status(player, ChatFormatting.AQUA, "message.manaessencebridge.pull_on");
        }
    }

    /**
     * Снимает верхний тир: возвращает катализатор, списывает ману из пула
     * и откатывает цвет к предыдущему тиру (или очищает у чистого пула).
     */
    private void removeTier(Level world, BlockPos pos, Player player, BlockEntity te,
                            ManaPool pool, InferiumCatalystCapability cap) {
        EssenceTier current = EssenceTier.byLevel(cap.getTier());
        if (current == null) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.not_upgraded");
            return;
        }

        int cost = current.getManaCost();
        if (pool.getCurrentMana() < cost) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.crystal_no_mana",
                    CatalystItem.format(cost));
            return;
        }

        // Ёмкость после снятия тира уменьшится. Если мана в неё не влезет,
        // лишнее сгорело бы - поэтому отказываем и просим сначала потратить.
        int reducedCapacity = PoolCapacity.capacityFor(cap, te, Math.max(current.getLevel() - 1, 1));
        if (pool.getCurrentMana() - cost > reducedCapacity) {
            status(player, ChatFormatting.YELLOW, "message.manaessencebridge.crystal_overflow",
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
        pool.setColor(below == null ? java.util.Optional.empty()
                                    : java.util.Optional.of(below.getPoolColor()));
        PoolCapacity.apply(world, pos, te, cap);
        syncPool(world, pos, te);
        ModNetwork.syncToTracking(world, pos, cap);
        if (!cap.isUpgraded()) {
            PoolAutoPull.untrack(world, pos);
        }

        ItemStack refund = new ItemStack(ModItems.getCatalyst(current).get());
        if (!player.getInventory().add(refund)) {
            player.drop(refund, false);
        }

        world.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE,
                SoundSource.BLOCKS, 0.8F, 1.3F);
        PoolEffects.burst(world, pos, current, 30, 0.4);
        status(player, ChatFormatting.LIGHT_PURPLE, "message.manaessencebridge.tier_removed",
                current.getLevel(), current.getDisplayName(), CatalystItem.format(cost));
    }

    // --- вспомогательное --------------------------------------------------

    /**
     * Свободное место в пуле. getAvailableSpaceForMana() лежит в публичном
     * API-интерфейсе SparkAttachable, который Mana Pool реализует, - так что
     * во внутренности Botania лезть не приходится.
     */
    private static int availableSpace(BlockEntity te) {
        if (te instanceof ManaPool) {
            ManaPool pool = (ManaPool) te;
            return Math.max(0, pool.getMaxMana() - pool.getCurrentMana());
        }
        return Integer.MAX_VALUE;
    }

    /** Сохраняем BlockEntity и рассылаем его клиентам - иначе цвет пула не обновится. */
    private static void syncPool(Level world, BlockPos pos, BlockEntity te) {
        if (te == null) {
            return;
        }
        te.setChanged();
        BlockState state = world.getBlockState(pos);
        world.sendBlockUpdated(pos, state, state, 3);
    }

    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Название тира как компонент; для несуществующего тира - его номер. */
    private static Object tierName(int level) {
        EssenceTier tier = EssenceTier.byLevel(level);
        return tier == null ? String.valueOf(level) : tier.getDisplayName();
    }

    /** Приватные пулы: чужим пулом распоряжается только хозяин (и операторы). */
    private static boolean mayAct(Player player, InferiumCatalystCapability cap) {
        if (PoolAccess.mayUse(player, cap.getOwner())) {
            return true;
        }
        status(player, ChatFormatting.RED, "message.manaessencebridge.not_your_pool");
        return false;
    }

    private static void status(Player player, ChatFormatting color, String key, Object... args) {
        MutableComponent message = Component.translatable(key, args);
        message.withStyle(color);
        player.displayClientMessage(message, true);
    }
}
