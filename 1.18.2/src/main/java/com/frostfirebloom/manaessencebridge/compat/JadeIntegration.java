package com.frostfirebloom.manaessencebridge.compat;

import net.minecraft.network.chat.TranslatableComponent;
import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.PoolAutoPull;
import com.frostfirebloom.manaessencebridge.PoolCapacity;
import com.frostfirebloom.manaessencebridge.client.ClientPoolTiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import mcp.mobius.waila.api.BlockAccessor;
import mcp.mobius.waila.api.IComponentProvider;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IServerDataProvider;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import mcp.mobius.waila.api.WailaPlugin;
import mcp.mobius.waila.api.config.IPluginConfig;
import vazkii.botania.api.mana.IManaPool;

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

    /** Jade 5 (1.18.2): и серверные, и клиентские провайдеры регистрируются здесь. */
    @Override
    public void register(IRegistrar registration) {
        // Состояние цветков живёт на сервере - Jade просит его отдельным провайдером.
        for (Class<? extends net.minecraft.world.level.block.entity.BlockEntity> c : java.util.Arrays.<Class<? extends net.minecraft.world.level.block.entity.BlockEntity>>asList(com.frostfirebloom.manaessencebridge.EssentideBlockEntity.class, com.frostfirebloom.manaessencebridge.BrookbellBlockEntity.class, com.frostfirebloom.manaessencebridge.BoltbloomBlockEntity.class, com.frostfirebloom.manaessencebridge.MelodiaBlockEntity.class, com.frostfirebloom.manaessencebridge.BumblebloomBlockEntity.class, com.frostfirebloom.manaessencebridge.WardeniaBlockEntity.class)) {
            registration.registerBlockDataProvider(new FlowerData(), c);
        }
        // Регистрируемся на все блоки и отсеиваем нужные внутри: так мы
        // не зависим от внутреннего класса блока Botania.
        registration.registerComponentProvider(new PoolProvider(), TooltipPosition.BODY, Block.class);
    }

    private static class PoolProvider implements IComponentProvider {

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
            if (!(accessor.getBlockEntity() instanceof IManaPool)) {
                return;
            }

            EssenceTier tier = EssenceTier.byLevel(ClientPoolTiers.get(accessor.getPosition()));
            if (tier == null) {
                return;
            }

            tooltip.add(new TranslatableComponent("hud.manaessencebridge.title",
                    tier.getLevel(), tier.getDisplayName()));
            tooltip.add(new TranslatableComponent("hud.manaessencebridge.rate",
                    tier.getDisplayName(),
                    CatalystItem.format(tier.getManaPerEssence()),
                    CatalystItem.format(tier.getManaCost())));
            // Клиентский BlockEntity пула знает свой запас маны - Botania сама его синхронизирует.
            tooltip.add(new TranslatableComponent("hud.manaessencebridge.stored",
                    CatalystItem.format(((IManaPool) accessor.getBlockEntity()).getCurrentMana()),
                    CatalystItem.format(PoolCapacity.maxMana(accessor.getBlockEntity()))));
            tooltip.add(new TranslatableComponent("hud.manaessencebridge.processed",
                    CatalystItem.format(ClientPoolTiers.getProcessed(accessor.getPosition()))));
            tooltip.add(new TranslatableComponent(PoolAutoPull.statusKey(
                    ClientPoolTiers.isPullEnabled(accessor.getPosition()),
                    accessor.getLevel().hasNeighborSignal(accessor.getPosition()))));
        }
    }

    /** Сервер: строки состояния цветка в данные Jade (JSON-компоненты). */
    private static class FlowerData implements IServerDataProvider<net.minecraft.world.level.block.entity.BlockEntity> {

        static final String KEY = "manaessencebridge_status";

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundTag data, net.minecraft.server.level.ServerPlayer player,
                                     net.minecraft.world.level.Level level, net.minecraft.world.level.block.entity.BlockEntity be,
                                     boolean showDetails) {
            if (be instanceof com.frostfirebloom.manaessencebridge.FlowerStatus) {
                net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                for (Component line : ((com.frostfirebloom.manaessencebridge.FlowerStatus) be).statusLines()) {
                    list.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(line)));
                }
                data.put(KEY, list);
            }
        }
    }
}
