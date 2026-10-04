package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IThrottledPacket;

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
 * вливает BlockEntityTag в NBT нового TileEntity, а TileEntity.read
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

        IWorld iWorld = event.getWorld();
        if (iWorld.isRemote() || !(iWorld instanceof World)) {
            return;
        }

        World world = (World) iWorld;
        BlockPos pos = event.getPos();
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof IManaPool)) {
            return;
        }

        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null || !cap.isUpgraded()) {
            return;
        }

        int tier = cap.getTier();
        PlayerEntity player = event.getPlayer();
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
        Block.spawnAsEntity(world, pos, createPoolStack(poolItem, cap, te));

        // Отменённое событие не проходит ванильный путь, а значит инструмент
        // не изнашивается. Списываем прочность сами, иначе прокачанный пул
        // ломался бы бесплатно, в отличие от любого другого блока.
        if (player != null && !player.isCreative()) {
            player.getHeldItemMainhand().damageItem(1, player,
                    p -> p.sendBreakAnimation(net.minecraft.util.Hand.MAIN_HAND));
        }

        EssenceTier essenceTier = EssenceTier.byLevel(tier);
        if (player != null && essenceTier != null) {
            player.sendStatusMessage(new TranslationTextComponent(
                    "message.manaessencebridge.tier_saved", tier, essenceTier.getDisplayName()), true);
        }
    }

    /**
     * Пул поставили обратно: тир уже восстановлен ванильным BlockItem,
     * осталось вернуть цвет (он живёт в TileEntity самой Botania и в предмет
     * не переезжает) и разослать тир клиентам для HUD.
     */
    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        IWorld iWorld = event.getWorld();
        if (iWorld.isRemote() || !(iWorld instanceof World)) {
            return;
        }

        World world = (World) iWorld;
        BlockPos pos = event.getPos();
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof IManaPool)) {
            return;
        }

        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null) {
            return;
        }

        EssenceTier tier = EssenceTier.byLevel(cap.getTier());
        if (tier != null) {
            ((IManaPool) te).setColor(tier.getPoolColor());
            te.markDirty();
            BlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            if (te instanceof IThrottledPacket) {
                ((IThrottledPacket) te).markDispatchable();
            }
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
    private static ItemStack createPoolStack(Item poolItem, InferiumCatalystCapability cap, TileEntity te) {
        ItemStack stack = new ItemStack(poolItem);

        CompoundNBT capTag = new CompoundNBT();
        capTag.putInt(TAG_TIER, cap.getTier());
        capTag.putLong("processed", cap.getProcessed());
        capTag.putBoolean("pull", cap.isPullEnabled());
        capTag.putInt("baseCap", cap.getBaseCapacity());
        if (cap.getOwner() != null) {
            capTag.putUniqueId("owner", cap.getOwner());
        }

        CompoundNBT forgeCaps = new CompoundNBT();
        forgeCaps.put(CAP_KEY, capTag);

        CompoundNBT blockTag = stack.getOrCreateChildTag(TAG_BLOCK_ENTITY);
        blockTag.put(TAG_FORGE_CAPS, forgeCaps);
        // Мана едет вместе с пулом: на 16-32 млн терять её при переносе обидно.
        // Botania сама прочитает "mana" и "manaCap" из BlockEntityTag при установке.
        if (BridgeConfig.keepManaOnBreak() && te instanceof vazkii.botania.api.mana.IManaPool) {
            int mana = ((vazkii.botania.api.mana.IManaPool) te).getCurrentMana();
            if (mana > 0) {
                blockTag.putInt("mana", mana);
                blockTag.putInt("manaCap", Math.max(mana, PoolCapacity.capacityFor(cap, te, Math.max(cap.getTier(), 1))));
            }
        }
        return stack;
    }
}
