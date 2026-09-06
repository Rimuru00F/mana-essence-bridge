package com.frostfirebloom.manaessencebridge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.mana.ManaPool;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Вешаем наши capability на КАЖДЫЙ BlockEntity, реализующий Botania-интерфейс
 * ManaPool. Это официальная публичная точка расширения Botania -
 * никакого Mixin, никакого прямого наследования внутреннего класса TilePool.
 *
 * Их две: наша собственная (тир пула) и стандартная предметная
 * (IItemHandler), через которую работает автоматизация.
 */
public class CapabilityAttachHandler {

    private static final String TAG_TIER = "tier";

    /** Ключ из первой версии мода, когда прокачка была просто "да/нет". */
    private static final String TAG_LEGACY_UPGRADED = "upgraded";

    @SubscribeEvent
    public void onAttachBlockEntityCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity te = event.getObject();

        // Проверяем, что это именно Mana Pool (или любой другой блок,
        // реализующий интерфейс ManaPool - например, из аддонов Botania)
        if (!(te instanceof ManaPool)) {
            return;
        }

        event.addCapability(
                new ResourceLocation(ManaEssenceBridge.MODID, "inferium_catalyst"),
                new CatalystProvider(te)
        );
    }

    private static class CatalystProvider implements ICapabilitySerializable<CompoundTag> {

        private final InferiumCatalystCapability instance = new InferiumCatalystCapability();
        private final LazyOptional<InferiumCatalystCapability> catalystOptional = LazyOptional.of(() -> instance);

        private final LazyOptional<IItemHandler> itemHandlerOptional;

        CatalystProvider(BlockEntity tile) {
            PoolItemHandler handler = new PoolItemHandler(tile);
            this.itemHandlerOptional = LazyOptional.of(() -> handler);
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
            if (cap == ForgeCapabilities.ITEM_HANDLER
                    && BridgeConfig.automationEnabled()
                    && instance.isUpgraded()) {
                return itemHandlerOptional.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_TIER, instance.getTier());
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            if (nbt.contains(TAG_TIER)) {
                instance.setTier(nbt.getInt(TAG_TIER));
            } else if (nbt.getBoolean(TAG_LEGACY_UPGRADED)) {
                // Пулы, прокачанные ещё до появления тиров, становятся тиром 1.
                instance.setTier(EssenceTier.INFERIUM.getLevel());
            }
        }
    }
}
