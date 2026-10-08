package com.frostfirebloom.manaessencebridge;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.mana.IManaPool;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Вешаем наши capability на КАЖДЫЙ TileEntity, реализующий Botania-интерфейс
 * IManaPool. Это официальная публичная точка расширения Botania -
 * никакого Mixin, никакого прямого наследования внутреннего класса TilePool.
 *
 * Их две: наша собственная (тир пула) и стандартная предметная
 * (IItemHandler), через которую работает автоматизация.
 */
public class CapabilityAttachHandler {

    private static final String TAG_TIER = "tier";
    private static final String TAG_PROCESSED = "processed";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_PULL = "pull";
    private static final String TAG_BASE_CAP = "baseCap";
    private static final String TAG_LINKS = "links";

    /** Ключ из первой версии мода, когда прокачка была просто "да/нет". */
    private static final String TAG_LEGACY_UPGRADED = "upgraded";

    @SubscribeEvent
    public void onAttachTileEntityCapabilities(AttachCapabilitiesEvent<TileEntity> event) {
        TileEntity te = event.getObject();

        // Проверяем, что это именно Mana Pool (или любой другой блок,
        // реализующий интерфейс IManaPool - например, из аддонов Botania)
        if (!(te instanceof IManaPool)) {
            return;
        }

        CatalystProvider provider = new CatalystProvider(te);
        event.addCapability(
                new ResourceLocation(ManaEssenceBridge.MODID, "inferium_catalyst"),
                provider
        );
        // Когда пул ломают или выгружают вместе с чанком, Forge зовёт этих
        // слушателей. Без этого труба, закэшировавшая наш обработчик,
        // продолжала бы слать эссенцию в уже несуществующий пул - и та пропадала бы.
        event.addListener(provider::invalidate);
    }

    private static class CatalystProvider implements ICapabilitySerializable<CompoundNBT> {

        private final InferiumCatalystCapability instance = new InferiumCatalystCapability();
        private final LazyOptional<InferiumCatalystCapability> catalystOptional = LazyOptional.of(() -> instance);

        private final PoolItemHandler handler;
        private LazyOptional<IItemHandler> itemHandlerOptional;

        CatalystProvider(TileEntity tile) {
            this.handler = new PoolItemHandler(tile);
            this.itemHandlerOptional = LazyOptional.of(() -> handler);
        }

        /**
         * Гасим ссылку, которую держат трубы, и заводим свежую - на случай,
         * если этот же блок потом снова окажется в мире. Нашу собственную
         * capability не трогаем: снаружи её никто не кэширует.
         */
        void invalidate() {
            LazyOptional<IItemHandler> old = itemHandlerOptional;
            itemHandlerOptional = LazyOptional.of(() -> handler);
            old.invalidate();
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            if (cap == ModCapabilities.INFERIUM_CATALYST_CAPABILITY) {
                return catalystOptional.cast();
            }
            // Предметный хендлер объявляем только у прокачанных пулов и только
            // если автоматизация включена - чтобы у обычных пулов ничего
            // не менялось в поведении с трубами и воронками.
            if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                    && BridgeConfig.automationEnabled()
                    && instance.isUpgraded()) {
                return itemHandlerOptional.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundNBT serializeNBT() {
            CompoundNBT tag = new CompoundNBT();
            tag.putInt(TAG_TIER, instance.getTier());
            tag.putLong(TAG_PROCESSED, instance.getProcessed());
            if (instance.getOwner() != null) {
                tag.putUniqueId(TAG_OWNER, instance.getOwner());
            }
            tag.putBoolean(TAG_PULL, instance.isPullEnabled());
            tag.putInt(TAG_BASE_CAP, instance.getBaseCapacity());
            if (!instance.getLinks().isEmpty()) {
                tag.putLongArray(TAG_LINKS, instance.getLinks().stream().mapToLong(net.minecraft.util.math.BlockPos::toLong).toArray());
            }
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundNBT nbt) {
            if (nbt.contains(TAG_TIER)) {
                instance.setTier(nbt.getInt(TAG_TIER));
            } else if (nbt.getBoolean(TAG_LEGACY_UPGRADED)) {
                // Пулы, прокачанные ещё до появления тиров, становятся тиром 1.
                instance.setTier(EssenceTier.INFERIUM.getLevel());
            }
            instance.setProcessed(nbt.getLong(TAG_PROCESSED));
            if (nbt.hasUniqueId(TAG_OWNER)) {
                instance.setOwner(nbt.getUniqueId(TAG_OWNER));
            }
            instance.setPullEnabled(nbt.getBoolean(TAG_PULL));
            instance.setBaseCapacity(nbt.getInt(TAG_BASE_CAP));
            java.util.List<net.minecraft.util.math.BlockPos> links = new java.util.ArrayList<>();
            for (long packed : nbt.getLongArray(TAG_LINKS)) {
                links.add(net.minecraft.util.math.BlockPos.fromLong(packed));
            }
            instance.setLinks(links);
        }
    }
}
