package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.botania.api.mana.ManaPool;

/**
 * Сохранение прокачки при переносе пула.
 *
 * Botania роняет пул простейшей лут-таблицей, без переноса данных блока -
 * сломанный прокачанный пул терял бы и тир, и все вложенные катализаторы.
 *
 * Раньше здесь подменялась лут-таблица с функцией copy_nbt, но та схема
 * зависела от разбора NBT-пути со строкой вида ForgeCaps."modid:cap".tier
 * и молча не срабатывала. Теперь предмет собирается руками: тир пишется
 * прямо в BlockEntityTag, без каких-либо путей и парсеров.
 *
 * Обратно его восстанавливает ванильный BlockItem: при установке блока он
 * вливает BlockEntityTag в NBT нового BlockEntity, а BlockEntity.read
 * разбирает оттуда секцию ForgeCaps - то есть нашу capability.
 */
public class PoolDropHandler {

    private static final String TAG_BLOCK_ENTITY = "BlockEntityTag";
    private static final String TAG_FORGE_CAPS = "ForgeCaps";
    private static final String TAG_TIER = "tier";

    /** Тот же ключ, под которым capability регистрируется в CapabilityAttachHandler. */
    private static final String CAP_KEY = ManaEssenceBridge.MODID + ":inferium_catalyst";

    /**
     * Пул сломали: роняем его сами, с тиром внутри предмета.
     *
     * Слушаем последними: если защита привата отменила поломку, до нас
     * событие уже не дойдёт, и мы ничего не уроним.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }

        LevelAccessor iWorld = event.getLevel();
        if (iWorld.isClientSide() || !(iWorld instanceof Level)) {
            return;
        }

        Level world = (Level) iWorld;
        BlockPos pos = event.getPos();
        BlockEntity te = world.getBlockEntity(pos);
        if (!(te instanceof ManaPool)) {
            return;
        }

        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null || !cap.isUpgraded()) {
            return;
        }

        int tier = cap.getTier();
        Player player = event.getPlayer();
        BlockState state = event.getState();

        // Клиент кеширует тиры по координатам - без этого HUD продолжит
        // показывать прокачку на месте уже сломанного пула.
        ModNetwork.syncRemoved(world, pos);
        PoolAutoPull.untrack(world, pos);

        // В креативе ванильный пул не выпадает вообще - не меняем это поведение.
        if (player != null && player.isCreative()) {
            return;
        }

        // Без кирки пул, как и живой камень, из которого он сделан, не выпадает
        // вовсе. Следуем тому же правилу, а не выдаём прокачанный пул голой рукой.
        if (player == null || !state.canHarvestBlock(world, pos, player)) {
            return;
        }

        // Роняем предмет именно этого блока, а не обычный пул: у Botania их
        // четыре вида (обычный, разбавленный, сказочный, творческий), плюс
        // пулы из аддонов. Раньше любой из них превращался в обычный.
        Item poolItem = state.getBlock().asItem();
        if (poolItem == Items.AIR) {
            return; // у блока нет предмета - пусть ломается как обычно
        }

        // Ломаем сами, чтобы вместо обычного пула выпал пул с тиром.
        event.setCanceled(true);
        world.removeBlock(pos, false);
        Block.popResource(world, pos, createPoolStack(poolItem, cap, te));

        // Отменённое событие не проходит ванильный путь, а значит инструмент
        // не изнашивается. Списываем прочность сами, иначе прокачанный пул
        // ломался бы бесплатно, в отличие от любого другого блока.
        if (player != null && !player.isCreative()) {
            player.getMainHandItem().hurtAndBreak(1, player,
                    p -> p.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        }

        EssenceTier essenceTier = EssenceTier.byLevel(tier);
        if (player != null && essenceTier != null) {
            player.displayClientMessage(Component.translatable(
                    "message.manaessencebridge.tier_saved", tier, essenceTier.getDisplayName()), true);
        }
    }

    /**
     * Пул поставили обратно: тир уже восстановлен ванильным BlockItem,
     * осталось вернуть цвет (он живёт в BlockEntity самой Botania и в предмет
     * не переезжает) и разослать тир клиентам для HUD.
     */
    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor iWorld = event.getLevel();
        if (iWorld.isClientSide() || !(iWorld instanceof Level)) {
            return;
        }

        Level world = (Level) iWorld;
        BlockPos pos = event.getPos();
        BlockEntity te = world.getBlockEntity(pos);
        if (!(te instanceof ManaPool)) {
            return;
        }

        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null) {
            return;
        }

        EssenceTier tier = EssenceTier.byLevel(cap.getTier());
        if (tier != null) {
            ((ManaPool) te).setColor(java.util.Optional.of(tier.getPoolColor()));
            te.setChanged();
            BlockState state = world.getBlockState(pos);
            world.sendBlockUpdated(pos, state, state, 3);
        }

        PoolCapacity.apply(world, pos, te, cap);

        // Шлём всегда, включая 0: на этом месте мог стоять прокачанный пул,
        // и у клиента остался бы его тир в кеше.
        ModNetwork.syncToTracking(world, pos, cap);
        if (cap.isUpgraded()) {
            PoolAutoPull.track(world, pos);
        }
    }

    /**
     * Пул-предмет с тиром, записанным так же, как его пишет сам Forge.
     * Оборот и хозяин переезжают вместе с ним: перенос пула не должен
     * обнулять счётчик достижений.
     */
    private static ItemStack createPoolStack(Item poolItem, InferiumCatalystCapability cap, BlockEntity te) {
        ItemStack stack = new ItemStack(poolItem);

        CompoundTag capTag = new CompoundTag();
        capTag.putInt(TAG_TIER, cap.getTier());
        capTag.putLong("processed", cap.getProcessed());
        capTag.putBoolean("pull", cap.isPullEnabled());
        capTag.putInt("baseCap", cap.getBaseCapacity());
        if (cap.getOwner() != null) {
            capTag.putUUID("owner", cap.getOwner());
        }

        CompoundTag forgeCaps = new CompoundTag();
        forgeCaps.put(CAP_KEY, capTag);

        CompoundTag blockTag = stack.getOrCreateTagElement(TAG_BLOCK_ENTITY);
        blockTag.put(TAG_FORGE_CAPS, forgeCaps);
        // Мана едет вместе с пулом: на 16-32 млн терять её при переносе обидно.
        // Botania сама прочитает "mana" и "manaCap" из BlockEntityTag при установке.
        if (BridgeConfig.keepManaOnBreak() && te instanceof vazkii.botania.api.mana.ManaPool) {
            int mana = ((vazkii.botania.api.mana.ManaPool) te).getCurrentMana();
            if (mana > 0) {
                blockTag.putInt("mana", mana);
                blockTag.putInt("manaCap", Math.max(mana, PoolCapacity.capacityFor(cap, te, Math.max(cap.getTier(), 1))));
            }
        }
        return stack;
    }
}
