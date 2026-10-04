package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.ClientPoolTiers;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IThrottledPacket;
import vazkii.botania.api.wand.IWandBindable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Логика Конденсатора эссенции.
 *
 * Раз в condenserIntervalTicks (по умолчанию 2 тика) берёт из пула ману на одну эссенцию
 * по цене выкупа и складывает эссенцию выбранного тира во внутренний слот.
 * Пул - либо привязанный Жезлом леса (как распределитель), либо любой
 * прокачанный пул вплотную. Резерв не даёт опустошить пул, который кормит
 * ещё что-то; сигнал редстоуна ставит на паузу, как у сифона.
 *
 * Баланс не страдает: выкуп на 25% дороже продажи, так что гонять эссенцию
 * по кругу через конденсатор и сифон - чистая потеря маны. Да и сифон из
 * конденсатора не тянет вовсе.
 */
public class EssenceCondenserBlockEntity extends TileEntity implements ITickableTileEntity, IWandBindable {

    /** Дальность привязки жезлом - как у распределителя Botania. */
    public static final int BIND_RANGE = 12;

    /** Одна эссенция за проход: пул тратится ровным потоком, а не рывком. */
    private static final int MAX_PER_CYCLE = 1;

    /** Луч, звон и искры - не чаще раза в секунду, иначе мельтешение. */
    private static final int EFFECT_INTERVAL = 20;

    /**
     * Режим «излишки»: работать, только пока пул заполнен больше чем на 90%.
     * Лишняя мана, которую пулу уже некуда девать, уходит в эссенцию.
     */
    private static final int SURPLUS = 90;

    private static final int[] RESERVES = {0, 25, 50, 75, SURPLUS};

    private static final Direction[] PUSH_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

    private static final String TAG_FILTER = "filter";
    private static final String TAG_RESERVE = "reserve";
    private static final String TAG_BUFFER = "buffer";
    private static final String TAG_BOUND = "bound";

    private int filterTier = 0;
    private int reserve = 0;
    private long lastEffects = -EFFECT_INTERVAL;
    private ItemStack buffer = ItemStack.EMPTY;
    @Nullable
    private BlockPos boundPool;
    /** Кто поставил конденсатор - для приватных пулов. */
    @Nullable
    private java.util.UUID owner;

    private final IItemHandler handler = new OutputHandler();
    private LazyOptional<IItemHandler> handlerOptional = LazyOptional.of(() -> handler);

    public EssenceCondenserBlockEntity() {
        super(ModBlocks.ESSENCE_CONDENSER_BE.get());
    }

    // --- работа ------------------------------------------------------------

    @Override
    public void tick() {
        if (world == null || world.isRemote) {
            return;
        }
        if (world.isBlockPowered(pos)) {
            return; // редстоун - пауза
        }
        long now = world.getGameTime();
        if (now % BridgeConfig.condenserIntervalTicks() == 0) {
            condense(world);
        }
        if (now % BridgeConfig.pullIntervalTicks() == 0) {
            pushOut(world);
        }
    }

    private void condense(World world) {
        EssenceTier tier = EssenceTier.byLevel(filterTier);
        if (tier == null || !tier.isEnabled()) {
            return;
        }
        Item essence = tier.getEssenceItem();
        if (essence == null || essence == Items.AIR) {
            return; // Mystical Agriculture нет в сборке
        }
        // Фильтр сменили, а в слоте ещё старая эссенция - ждём, пока её заберут.
        if (!buffer.isEmpty() && buffer.getItem() != essence) {
            return;
        }
        int room = new ItemStack(essence).getMaxStackSize() - buffer.getCount();
        if (room <= 0) {
            return;
        }

        TileEntity poolTe = findPool(world);
        if (poolTe == null) {
            return;
        }
        InferiumCatalystCapability cap =
                poolTe.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null || !cap.supports(tier)) {
            return;
        }
        IManaPool pool = (IManaPool) poolTe;

        int cost = tier.getManaCost();
        long keep = (long) PoolCapacity.maxMana(poolTe) * reserve / 100L;
        long spendable = pool.getCurrentMana() - keep;
        if (spendable < cost) {
            return;
        }
        long count = Math.min(spendable / cost, room);
        count = Math.min(count, MAX_PER_CYCLE);
        count = Math.min(count, Integer.MAX_VALUE / cost);
        if (count <= 0) {
            return;
        }
        int mana = (int) (cost * count);

        pool.receiveMana(-mana);
        poolTe.markDirty();
        BlockState poolState = world.getBlockState(poolTe.getPos());
        world.notifyBlockUpdate(poolTe.getPos(), poolState, poolState, 3);
        if (poolTe instanceof IThrottledPacket) {
            ((IThrottledPacket) poolTe).markDispatchable();
        }
        PoolThroughput.record(world, poolTe.getPos(), cap, mana, null, false);
        PoolLedger.noteCondenser(world, poolTe.getPos(), filterTier, reserve);
        boolean effects = world.getGameTime() - lastEffects >= EFFECT_INTERVAL;
        if (effects) {
            lastEffects = world.getGameTime();
            PoolEffects.beam(world, poolTe.getPos(), pos, tier);
            // Тихий перезвон - слышно, что конденсатор работает,
            // но не раздражает: громкость в десять раз ниже обычной.
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.BLOCKS,
                    0.12F, 1.6F + world.rand.nextFloat() * 0.4F);
        }

        if (buffer.isEmpty()) {
            buffer = new ItemStack(essence, (int) count);
        } else {
            buffer.grow((int) count);
        }
        contentsChanged();
        if (effects) {
            PoolEffects.burst(world, pos, tier, Math.min(2 + (int) count, 8), 0.2);
        }
    }

    /** Что сейчас лежит внутри - клиент рисует это над конденсатором. */
    public ItemStack getDisplayedStack() {
        return buffer;
    }

    /**
     * Выталкивает готовую эссенцию в соседние контейнеры: сундуки, бочки,
     * воронки, трубы - сначала вниз, потом в стороны, потом вверх.
     * Пулы и другие конденсаторы пропускаем: пул превратил бы эссенцию
     * обратно в ману, и они гоняли бы её по кругу, сжигая четверть на каждом витке.
     */
    private void pushOut(World world) {
        if (buffer.isEmpty()) {
            return;
        }
        for (Direction dir : PUSH_ORDER) {
            BlockPos np = pos.offset(dir);
            if (!world.isBlockPresent(np)) {
                continue;
            }
            TileEntity te = world.getTileEntity(np);
            if (te == null || te instanceof IManaPool || te instanceof EssenceCondenserBlockEntity) {
                continue;
            }
            IItemHandler inv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir.getOpposite())
                    .orElse(null);
            if (inv == null) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(inv, buffer.copy(), false);
            if (rest.getCount() != buffer.getCount()) {
                buffer = rest.isEmpty() ? ItemStack.EMPTY : rest;
                contentsChanged();
            }
            if (buffer.isEmpty()) {
                return;
            }
        }
    }

    /** Привязанный пул, а если привязки нет - первый прокачанный пул вплотную. */
    @Nullable
    private TileEntity findPool(World world) {
        if (boundPool != null) {
            if (!world.isBlockPresent(boundPool)) {
                return null;
            }
            TileEntity te = world.getTileEntity(boundPool);
            return isUpgradedPool(te) ? te : null;
        }
        for (Direction dir : Direction.values()) {
            BlockPos np = pos.offset(dir);
            if (!world.isBlockPresent(np)) {
                continue;
            }
            TileEntity te = world.getTileEntity(np);
            if (isUpgradedPool(te)) {
                return te;
            }
        }
        return null;
    }

    public void setOwner(@Nullable java.util.UUID owner) {
        this.owner = owner;
        markDirty();
    }

    private boolean isUpgradedPool(@Nullable TileEntity te) {
        if (!(te instanceof IManaPool)) {
            return false;
        }
        InferiumCatalystCapability cap =
                te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return cap != null && cap.isUpgraded()
                && (world == null || world.isRemote || PoolAccess.condenserMayUse(owner, cap.getOwner()));
    }

    /**
     * Клиенту нужно состояние конденсатора для HUD жезла и Jade - шлём
     * его обычным пакетом блок-сущности при каждом изменении.
     */
    private void syncToClient() {
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, getBlockState(), getBlockState(), 3);
        }
    }

    private void contentsChanged() {
        markDirty();
        syncToClient();
        if (world != null) {
            world.updateComparatorOutputLevel(pos, getBlockState().getBlock());
        }
    }

    // --- клики игрока ------------------------------------------------------

    void setFilter(PlayerEntity player, EssenceTier tier) {
        filterTier = tier.getLevel();
        markDirty();
        syncToClient();
        click();
        status(player, TextFormatting.AQUA, "message.manaessencebridge.condenser_filter", tier.getDisplayName());
    }

    void cycleReserve(PlayerEntity player) {
        int next = 0;
        for (int i = 0; i < RESERVES.length; i++) {
            if (RESERVES[i] == reserve) {
                next = (i + 1) % RESERVES.length;
                break;
            }
        }
        reserve = RESERVES[next];
        markDirty();
        syncToClient();
        click();
        if (reserve == 0) {
            status(player, TextFormatting.GOLD, "message.manaessencebridge.condenser_reserve_zero");
        } else if (reserve == SURPLUS) {
            status(player, TextFormatting.LIGHT_PURPLE, "message.manaessencebridge.condenser_reserve_surplus");
        } else {
            status(player, TextFormatting.AQUA, "message.manaessencebridge.condenser_reserve", reserve);
        }
    }

    /** Пустая рука: отдать накопленную эссенцию и показать, как настроен конденсатор. */
    void handOut(PlayerEntity player) {
        if (!buffer.isEmpty()) {
            ItemStack out = buffer;
            buffer = ItemStack.EMPTY;
            contentsChanged();
            if (!player.inventory.addItemStackToInventory(out)) {
                player.dropItem(out, false);
            }
            if (world != null) {
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.3F, 1.4F);
            }
        }
        EssenceTier tier = EssenceTier.byLevel(filterTier);
        Object makes = tier == null
                ? new TranslationTextComponent("message.manaessencebridge.condenser_no_filter")
                : tier.getDisplayName();
        status(player, TextFormatting.GRAY, "message.manaessencebridge.condenser_status", makes, reserve, poolStatus(world));
    }

    /** Строки состояния для HUD жезла, Jade и TOP. Работает на обеих сторонах. */
    public List<ITextComponent> infoLines(@Nullable World world) {
        List<ITextComponent> lines = new ArrayList<>();
        EssenceTier tier = EssenceTier.byLevel(filterTier);
        lines.add(new TranslationTextComponent("hud.manaessencebridge.condenser_makes", tier == null
                ? new TranslationTextComponent("message.manaessencebridge.condenser_no_filter")
                : tier.getDisplayName()));
        lines.add(new TranslationTextComponent("hud.manaessencebridge.condenser_reserve", reserve));
        lines.add(new TranslationTextComponent("hud.manaessencebridge.condenser_pool", poolStatus(world)));
        lines.add(new TranslationTextComponent("hud.manaessencebridge.condenser_holding", buffer.getCount()));
        return lines;
    }

    private ITextComponent poolStatus(@Nullable World world) {
        if (boundPool != null) {
            return new TranslationTextComponent("message.manaessencebridge.condenser_pool_bound",
                    boundPool.getX(), boundPool.getY(), boundPool.getZ());
        }
        if (world != null && hasAdjacentPool(world)) {
            return new TranslationTextComponent("message.manaessencebridge.condenser_pool_adjacent");
        }
        return new TranslationTextComponent("message.manaessencebridge.condenser_pool_none");
    }

    /**
     * На сервере тир пула лежит в capability, а на клиенте её никто не
     * заполняет - там тир берём из кеша, который и так приходит для HUD.
     */
    private boolean hasAdjacentPool(World world) {
        if (!world.isRemote) {
            return findPool(world) != null;
        }
        for (Direction dir : Direction.values()) {
            BlockPos np = pos.offset(dir);
            if (world.getTileEntity(np) instanceof IManaPool && ClientPoolTiers.get(np) > 0) {
                return true;
            }
        }
        return false;
    }


    int comparatorSignal() {
        if (buffer.isEmpty()) {
            return 0;
        }
        return 1 + (int) (buffer.getCount() * 14L / Math.max(1, buffer.getMaxStackSize()));
    }

    void dropContents() {
        if (world != null && !buffer.isEmpty()) {
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), buffer);
            buffer = ItemStack.EMPTY;
        }
    }

    private void click() {
        if (world != null) {
            world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.3F, 1.2F);
        }
    }

    private static void status(PlayerEntity player, TextFormatting color, String key, Object... args) {
        player.sendStatusMessage(new TranslationTextComponent(key, args).mergeStyle(color), true);
    }

    // --- привязка жезлом Botania ------------------------------------------

    @Override
    public boolean canSelect(PlayerEntity player, ItemStack wand, BlockPos target, Direction side) {
        return true;
    }

    /**
     * Жезол зовёт это, когда игрок выбрал конденсатор и кликнул по другому блоку.
     * Прокачанный пул в радиусе - привязка; любой другой блок - сброс привязки,
     * и конденсатор снова работает с пулом вплотную.
     */
    @Override
    public boolean bindTo(PlayerEntity player, ItemStack wand, BlockPos target, Direction side) {
        if (world == null || target.equals(pos)) {
            return false;
        }
        TileEntity te = world.getTileEntity(target);
        if (!(te instanceof IManaPool)) {
            boundPool = null;
            bindingChanged();
            if (!world.isRemote && player != null) {
                status(player, TextFormatting.GRAY, "message.manaessencebridge.condenser_unbound");
            }
            return true;
        }
        if (!target.withinDistance(pos, BIND_RANGE + 0.5)) {
            if (!world.isRemote && player != null) {
                status(player, TextFormatting.RED, "message.manaessencebridge.condenser_too_far", BIND_RANGE);
            }
            return false;
        }
        InferiumCatalystCapability poolCap = te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!world.isRemote && player != null && poolCap != null && !PoolAccess.mayUse(player, poolCap.getOwner())) {
            status(player, TextFormatting.RED, "message.manaessencebridge.not_your_pool");
            return false;
        }
        boundPool = target.toImmutable();
        bindingChanged();
        if (!world.isRemote && player != null) {
            status(player, TextFormatting.AQUA, "message.manaessencebridge.condenser_bound");
        }
        if (player instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            PoolThroughput.award((net.minecraft.entity.player.ServerPlayerEntity) player, "condenser_link");
        }
        return true;
    }

    @Nullable
    @Override
    public BlockPos getBinding() {
        return boundPool;
    }

    /** Жезол рисует линию привязки на клиенте - ему нужна позиция и там. */
    private void bindingChanged() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, getBlockState(), getBlockState(), 3);
        }
    }

    // --- сохранение и синхронизация ----------------------------------------

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        super.write(tag);
        tag.putInt(TAG_FILTER, filterTier);
        tag.putInt(TAG_RESERVE, reserve);
        if (!buffer.isEmpty()) {
            tag.put(TAG_BUFFER, buffer.write(new CompoundNBT()));
        }
        if (boundPool != null) {
            tag.put(TAG_BOUND, NBTUtil.writeBlockPos(boundPool));
        }
        if (owner != null) {
            tag.putUniqueId("owner", owner);
        }
        return tag;
    }

    @Override
    public void read(BlockState state, CompoundNBT tag) {
        super.read(state, tag);
        filterTier = tag.getInt(TAG_FILTER);
        reserve = 0;
        for (int r : RESERVES) {
            if (r == tag.getInt(TAG_RESERVE)) {
                reserve = r;
            }
        }
        buffer = tag.contains(TAG_BUFFER) ? ItemStack.read(tag.getCompound(TAG_BUFFER)) : ItemStack.EMPTY;
        boundPool = tag.contains(TAG_BOUND) ? NBTUtil.readBlockPos(tag.getCompound(TAG_BOUND)) : null;
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
    }

    /** В 1.16.5 read() берёт координаты из тега, поэтому шлём полный тег, а не выборку. */
    @Override
    public CompoundNBT getUpdateTag() {
        return write(new CompoundNBT());
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket packet) {
        read(getBlockState(), packet.getNbtCompound());
    }

    // --- трубы и воронки: только забирать ----------------------------------

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return handlerOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void remove() {
        super.remove();
        handlerOptional.invalidate();
    }

    @Override
    public void validate() {
        super.validate();
        handlerOptional = LazyOptional.of(() -> handler);
    }

    private class OutputHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return buffer;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return stack; // внутрь ничего не принимаем
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (buffer.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, buffer.getCount());
            ItemStack out = buffer.copy();
            out.setCount(n);
            if (!simulate) {
                buffer.shrink(n);
                if (buffer.isEmpty()) {
                    buffer = ItemStack.EMPTY;
                }
                contentsChanged();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return false;
        }
    }
}
