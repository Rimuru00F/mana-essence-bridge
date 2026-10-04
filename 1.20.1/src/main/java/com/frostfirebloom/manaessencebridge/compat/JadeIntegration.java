package com.frostfirebloom.manaessencebridge.compat;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.frostfirebloom.manaessencebridge.PoolAutoPull;
import com.frostfirebloom.manaessencebridge.PoolCapacity;
import com.frostfirebloom.manaessencebridge.client.ClientPoolTiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import vazkii.botania.api.mana.ManaPool;

/**
 * Показ тира и курса в Jade.
 *
 * В отличие от TOP, провайдеры Jade выполняются на клиенте, поэтому тир
 * берётся из клиентского кеша, который мод и так наполняет пакетами.
 *
 * Класс ищет и загружает сама Jade по аннотации - если её нет в сборке,
 * он никогда не будет загружен.
 */
@WailaPlugin
public class JadeIntegration implements IWailaPlugin {

    @Override
    public void register(snownee.jade.api.IWailaCommonRegistration registration) {
        // Состояние цветков живёт на сервере - Jade просит его отдельным провайдером.
        for (Class<? extends net.minecraft.world.level.block.entity.BlockEntity> c : java.util.Arrays.<Class<? extends net.minecraft.world.level.block.entity.BlockEntity>>asList(com.frostfirebloom.manaessencebridge.EssentideBlockEntity.class, com.frostfirebloom.manaessencebridge.BrookbellBlockEntity.class, com.frostfirebloom.manaessencebridge.BoltbloomBlockEntity.class, com.frostfirebloom.manaessencebridge.MelodiaBlockEntity.class, com.frostfirebloom.manaessencebridge.BumblebloomBlockEntity.class, com.frostfirebloom.manaessencebridge.WardeniaBlockEntity.class)) {
            registration.registerBlockDataProvider(new FlowerData(), c);
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Регистрируемся на все блоки и отсеиваем нужные внутри: так мы
        // не зависим от внутреннего класса блока Botania.
        registration.registerBlockComponent(new PoolProvider(), Block.class);
    }

    private static class PoolProvider implements IBlockComponentProvider {

        private static final ResourceLocation UID =
                new ResourceLocation(ManaEssenceBridge.MODID, "mana_pool");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof com.frostfirebloom.manaessencebridge.FlowerStatus) {
                net.minecraft.nbt.ListTag list = accessor.getServerData().getList(FlowerData.KEY, net.minecraft.nbt.Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    Component line = Component.Serializer.fromJson(list.getString(i));
                    if (line != null) {
                        tooltip.add(line);
                    }
                }
                return;
            }
            if (accessor.getBlockEntity() instanceof EssenceCondenserBlockEntity) {
                for (Component line : ((EssenceCondenserBlockEntity) accessor.getBlockEntity())
                        .infoLines(accessor.getLevel())) {
                    tooltip.add(line);
                }
                return;
            }
            if (!(accessor.getBlockEntity() instanceof ManaPool)) {
                return;
            }

            EssenceTier tier = EssenceTier.byLevel(ClientPoolTiers.get(accessor.getPosition()));
            if (tier == null) {
                return;
            }

            tooltip.add(Component.translatable("hud.manaessencebridge.title",
                    tier.getLevel(), tier.getDisplayName()));
            tooltip.add(Component.translatable("hud.manaessencebridge.rate",
                    tier.getDisplayName(),
                    CatalystItem.format(tier.getManaPerEssence()),
                    CatalystItem.format(tier.getManaCost())));
            // Клиентский BlockEntity пула знает свой запас маны - Botania сама его синхронизирует.
            tooltip.add(Component.translatable("hud.manaessencebridge.stored",
                    CatalystItem.format(((ManaPool) accessor.getBlockEntity()).getCurrentMana()),
                    CatalystItem.format(PoolCapacity.maxMana(accessor.getBlockEntity()))));
            tooltip.add(Component.translatable("hud.manaessencebridge.processed",
                    CatalystItem.format(ClientPoolTiers.getProcessed(accessor.getPosition()))));
            tooltip.add(Component.translatable(PoolAutoPull.statusKey(
                    ClientPoolTiers.isPullEnabled(accessor.getPosition()),
                    accessor.getLevel().hasNeighborSignal(accessor.getPosition()))));
        }
    }

    /** Сервер: строки состояния цветка в данные Jade (JSON-компоненты). */
    private static class FlowerData implements snownee.jade.api.IServerDataProvider<snownee.jade.api.BlockAccessor> {

        static final String KEY = "manaessencebridge_status";

        @Override
        public ResourceLocation getUid() {
            return new ResourceLocation(ManaEssenceBridge.MODID, "flower_status");
        }

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundTag data, snownee.jade.api.BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof com.frostfirebloom.manaessencebridge.FlowerStatus) {
                net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                for (Component line : ((com.frostfirebloom.manaessencebridge.FlowerStatus) accessor.getBlockEntity()).statusLines()) {
                    list.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(line)));
                }
                data.put(KEY, list);
            }
        }
    }
}
