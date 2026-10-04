package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.ClientPoolTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.block.WandBindable;
import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.mana.ManaPool;

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
public class EssenceCondenserBlockEntity extends BlockEntity implements WandBindable {

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

    /**
     * Клик жезлом, который не стал привязкой, меняет резерв - запасной путь
     * на случай, если Shift+ПКМ пустой рукой перехватывает другой мод.
     */
    private final Wandable wandable = (player, stack, side) -> {
        if (player != null && level != null && !level.isClientSide) {
            cycleReserve(player);
        }
        return true;
    };
    private LazyOptional<Wandable> wandableOptional = LazyOptional.of(() -> wandable);

    public EssenceCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ESSENCE_CONDENSER_BE.get(), pos, state);
    }

    // --- работа ------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssenceCondenserBlockEntity be) {
        if (level.hasNeighborSignal(pos)) {
            return; // редстоун - пауза
        }
        long now = level.getGameTime();
        if (now % BridgeConfig.condenserIntervalTicks() == 0) {
            be.condense(level);
        }
        if (now % BridgeConfig.pullIntervalTicks() == 0) {
            be.pushOut(level);
        }
    }

    private void condense(Level level) {
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

        BlockEntity poolBe = findPool(level);
        if (poolBe == null) {
            return;
        }
        InferiumCatalystCapability cap =
                poolBe.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (cap == null || !cap.supports(tier)) {
            return;
        }
        ManaPool pool = (ManaPool) poolBe;

        int cost = tier.getManaCost();
        long keep = (long) PoolCapacity.maxMana(poolBe) * reserve / 100L;
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
        poolBe.setChanged();
        BlockState poolState = level.getBlockState(poolBe.getBlockPos());
        level.sendBlockUpdated(poolBe.getBlockPos(), poolState, poolState, 3);
        PoolThroughput.record(level, poolBe.getBlockPos(), cap, mana, null, false);
        PoolLedger.noteCondenser(level, poolBe.getBlockPos(), filterTier, reserve);
        boolean effects = level.getGameTime() - lastEffects >= EFFECT_INTERVAL;
        if (effects) {
            lastEffects = level.getGameTime();
            PoolEffects.beam(level, poolBe.getBlockPos(), worldPosition, tier);
            // Тихий перезвон - слышно, что конденсатор работает,
            // но не раздражает: громкость в десять раз ниже обычной.
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.12F, 1.6F + level.random.nextFloat() * 0.4F);
        }

        if (buffer.isEmpty()) {
            buffer = new ItemStack(essence, (int) count);
        } else {
            buffer.grow((int) count);
        }
        contentsChanged();
        if (effects) {
            PoolEffects.burst(level, worldPosition, tier, Math.min(2 + (int) count, 8), 0.2);
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
    private void pushOut(Level level) {
        if (buffer.isEmpty()) {
            return;
        }
        for (Direction dir : PUSH_ORDER) {
            BlockPos np = worldPosition.relative(dir);
            if (!level.isLoaded(np)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(np);
            if (be == null || be instanceof ManaPool || be instanceof EssenceCondenserBlockEntity) {
                continue;
            }
            IItemHandler inv = be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).orElse(null);
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
    private BlockEntity findPool(Level level) {
        if (boundPool != null) {
            if (!level.isLoaded(boundPool)) {
                return null;
            }
            BlockEntity be = level.getBlockEntity(boundPool);
            return isUpgradedPool(be) ? be : null;
        }
        for (Direction dir : Direction.values()) {
            BlockPos np = worldPosition.relative(dir);
            if (!level.isLoaded(np)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(np);
            if (isUpgradedPool(be)) {
                return be;
            }
        }
        return null;
    }

    public void setOwner(@Nullable java.util.UUID owner) {
        this.owner = owner;
        setChanged();
    }

    private boolean isUpgradedPool(@Nullable BlockEntity be) {
        if (!(be instanceof ManaPool)) {
            return false;
        }
        InferiumCatalystCapability cap =
                be.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        return cap != null && cap.isUpgraded()
                && (level == null || level.isClientSide || PoolAccess.condenserMayUse(owner, cap.getOwner()));
    }

    /**
     * Клиенту нужно состояние конденсатора для HUD жезла и Jade - шлём
     * его обычным пакетом блок-сущности при каждом изменении.
     */
    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void contentsChanged() {
        setChanged();
        syncToClient();
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // --- клики игрока ------------------------------------------------------

    void setFilter(Player player, EssenceTier tier) {
        filterTier = tier.getLevel();
        setChanged();
        syncToClient();
        click();
        status(player, ChatFormatting.AQUA, "message.manaessencebridge.condenser_filter", tier.getDisplayName());
    }

    void cycleReserve(Player player) {
        int next = 0;
        for (int i = 0; i < RESERVES.length; i++) {
            if (RESERVES[i] == reserve) {
                next = (i + 1) % RESERVES.length;
                break;
            }
        }
        reserve = RESERVES[next];
        setChanged();
        syncToClient();
        click();
        if (reserve == 0) {
            status(player, ChatFormatting.GOLD, "message.manaessencebridge.condenser_reserve_zero");
        } else if (reserve == SURPLUS) {
            status(player, ChatFormatting.LIGHT_PURPLE, "message.manaessencebridge.condenser_reserve_surplus");
        } else {
            status(player, ChatFormatting.AQUA, "message.manaessencebridge.condenser_reserve", reserve);
        }
    }

    /** Пустая рука: отдать накопленную эссенцию и показать, как настроен конденсатор. */
    void handOut(Player player) {
        if (!buffer.isEmpty()) {
            ItemStack out = buffer;
            buffer = ItemStack.EMPTY;
            contentsChanged();
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.4F);
            }
        }
        EssenceTier tier = EssenceTier.byLevel(filterTier);
        Component makes = tier == null
                ? Component.translatable("message.manaessencebridge.condenser_no_filter")
                : tier.getDisplayName();
        status(player, ChatFormatting.GRAY, "message.manaessencebridge.condenser_status", makes, reserve, poolStatus(level));
    }

    /** Строки состояния для HUD жезла, Jade и TOP. Работает на обеих сторонах. */
    public List<Component> infoLines(@Nullable Level level) {
        List<Component> lines = new ArrayList<>();
        EssenceTier tier = EssenceTier.byLevel(filterTier);
        lines.add(Component.translatable("hud.manaessencebridge.condenser_makes", tier == null
                ? Component.translatable("message.manaessencebridge.condenser_no_filter")
                : tier.getDisplayName()));
        lines.add(Component.translatable("hud.manaessencebridge.condenser_reserve", reserve));
        lines.add(Component.translatable("hud.manaessencebridge.condenser_pool", poolStatus(level)));
        lines.add(Component.translatable("hud.manaessencebridge.condenser_holding", buffer.getCount()));
        return lines;
    }

    private Component poolStatus(@Nullable Level level) {
        if (boundPool != null) {
            return Component.translatable("message.manaessencebridge.condenser_pool_bound",
                    boundPool.getX(), boundPool.getY(), boundPool.getZ());
        }
        if (level != null && hasAdjacentPool(level)) {
            return Component.translatable("message.manaessencebridge.condenser_pool_adjacent");
        }
        return Component.translatable("message.manaessencebridge.condenser_pool_none");
    }

    /**
     * На сервере тир пула лежит в capability, а на клиенте её никто не
     * заполняет - там тир берём из кеша, который и так приходит для HUD.
     */
    private boolean hasAdjacentPool(Level level) {
        if (!level.isClientSide) {
            return findPool(level) != null;
        }
        for (Direction dir : Direction.values()) {
            BlockPos np = worldPosition.relative(dir);
            if (level.getBlockEntity(np) instanceof ManaPool && ClientPoolTiers.get(np) > 0) {
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
        if (level != null && !buffer.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), buffer);
            buffer = ItemStack.EMPTY;
        }
    }

    private void click() {
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK, SoundSource.BLOCKS, 0.3F, 1.2F);
        }
    }

    private static void status(Player player, ChatFormatting color, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }

    // --- привязка жезлом Botania ------------------------------------------

    @Override
    public boolean canSelect(Player player, ItemStack wand, BlockPos pos, Direction side) {
        return true;
    }

    /**
     * Жезол зовёт это, когда игрок выбрал конденсатор и кликнул по другому блоку.
     * Прокачанный пул в радиусе - привязка; любой другой блок - сброс привязки,
     * и конденсатор снова работает с пулом вплотную.
     */
    @Override
    public boolean bindTo(Player player, ItemStack wand, BlockPos pos, Direction side) {
        if (level == null || pos.equals(worldPosition)) {
            return false;
        }
        BlockEntity target = level.getBlockEntity(pos);
        if (!(target instanceof ManaPool)) {
            boundPool = null;
            bindingChanged();
            if (!level.isClientSide && player != null) {
                status(player, ChatFormatting.GRAY, "message.manaessencebridge.condenser_unbound");
            }
            return true;
        }
        if (!pos.closerThan(worldPosition, BIND_RANGE + 0.5)) {
            if (!level.isClientSide && player != null) {
                status(player, ChatFormatting.RED, "message.manaessencebridge.condenser_too_far", BIND_RANGE);
            }
            return false;
        }
        InferiumCatalystCapability poolCap = target.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!level.isClientSide && player != null && poolCap != null && !PoolAccess.mayUse(player, poolCap.getOwner())) {
            status(player, ChatFormatting.RED, "message.manaessencebridge.not_your_pool");
            return false;
        }
        boundPool = pos.immutable();
        bindingChanged();
        if (!level.isClientSide && player != null) {
            status(player, ChatFormatting.AQUA, "message.manaessencebridge.condenser_bound");
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer) {
            PoolThroughput.award((net.minecraft.server.level.ServerPlayer) player, "condenser_link");
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
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // --- сохранение и синхронизация ----------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_FILTER, filterTier);
        tag.putInt(TAG_RESERVE, reserve);
        if (!buffer.isEmpty()) {
            tag.put(TAG_BUFFER, buffer.save(new CompoundTag()));
        }
        if (boundPool != null) {
            tag.put(TAG_BOUND, NbtUtils.writeBlockPos(boundPool));
        }
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        filterTier = tag.getInt(TAG_FILTER);
        reserve = 0;
        for (int r : RESERVES) {
            if (r == tag.getInt(TAG_RESERVE)) {
                reserve = r;
            }
        }
        buffer = tag.contains(TAG_BUFFER) ? ItemStack.of(tag.getCompound(TAG_BUFFER)) : ItemStack.EMPTY;
        boundPool = tag.contains(TAG_BOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_BOUND)) : null;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- трубы и воронки: только забирать ----------------------------------

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return handlerOptional.cast();
        }
        if (cap == BotaniaForgeCapabilities.WANDABLE) {
            return wandableOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handlerOptional.invalidate();
        wandableOptional.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        handlerOptional = LazyOptional.of(() -> handler);
        wandableOptional = LazyOptional.of(() -> wandable);
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
