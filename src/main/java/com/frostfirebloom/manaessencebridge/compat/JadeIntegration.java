package com.frostfirebloom.manaessencebridge.compat;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.PoolAutoPull;
import com.frostfirebloom.manaessencebridge.PoolCapacity;
import com.frostfirebloom.manaessencebridge.client.ClientPoolTiers;
import mcp.mobius.waila.api.IComponentProvider;
import mcp.mobius.waila.api.IDataAccessor;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import mcp.mobius.waila.api.WailaPlugin;
import net.minecraft.block.Block;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import vazkii.botania.api.mana.IManaPool;

import java.util.List;

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
    public void register(IRegistrar registrar) {
        // Регистрируемся на все блоки и отсеиваем нужные внутри: так мы
        // не зависим от внутреннего класса блока Botania.
        registrar.registerComponentProvider(new PoolProvider(), TooltipPosition.BODY, Block.class);
        // Состояние цветков живёт на сервере - Jade просит его отдельным провайдером.
        for (Class<?> c : new Class<?>[]{com.frostfirebloom.manaessencebridge.EssentideBlockEntity.class, com.frostfirebloom.manaessencebridge.BrookbellBlockEntity.class, com.frostfirebloom.manaessencebridge.BoltbloomBlockEntity.class, com.frostfirebloom.manaessencebridge.MelodiaBlockEntity.class, com.frostfirebloom.manaessencebridge.BumblebloomBlockEntity.class, com.frostfirebloom.manaessencebridge.WardeniaBlockEntity.class}) {
            registrar.registerBlockDataProvider(new FlowerData(), c);
        }
    }

    private static class PoolProvider implements IComponentProvider {

        @Override
        public void appendBody(List<ITextComponent> tooltip, IDataAccessor accessor, IPluginConfig config) {
            if (accessor.getTileEntity() instanceof com.frostfirebloom.manaessencebridge.FlowerStatus) {
                net.minecraft.nbt.ListNBT list = accessor.getServerData().getList(FlowerData.KEY, 8);
                for (int i = 0; i < list.size(); i++) {
                    ITextComponent line = ITextComponent.Serializer.getComponentFromJson(list.getString(i));
                    if (line != null) {
                        tooltip.add(line);
                    }
                }
                return;
            }
            if (accessor.getTileEntity() instanceof EssenceCondenserBlockEntity) {
                tooltip.addAll(((EssenceCondenserBlockEntity) accessor.getTileEntity())
                        .infoLines(accessor.getWorld()));
                return;
            }
            if (!(accessor.getTileEntity() instanceof IManaPool)) {
                return;
            }

            EssenceTier tier = EssenceTier.byLevel(ClientPoolTiers.get(accessor.getPosition()));
            if (tier == null) {
                return;
            }

            tooltip.add(new TranslationTextComponent("hud.manaessencebridge.title",
                    tier.getLevel(), tier.getDisplayName()));
            tooltip.add(new TranslationTextComponent("hud.manaessencebridge.rate",
                    tier.getDisplayName(),
                    CatalystItem.format(tier.getManaPerEssence()),
                    CatalystItem.format(tier.getManaCost())));
            // Клиентский TileEntity пула знает свой запас маны - Botania сама его синхронизирует.
            tooltip.add(new TranslationTextComponent("hud.manaessencebridge.stored",
                    CatalystItem.format(((IManaPool) accessor.getTileEntity()).getCurrentMana()),
                    CatalystItem.format(PoolCapacity.maxMana(accessor.getTileEntity()))));
            tooltip.add(new TranslationTextComponent("hud.manaessencebridge.processed",
                    CatalystItem.format(ClientPoolTiers.getProcessed(accessor.getPosition()))));
            tooltip.add(new TranslationTextComponent(PoolAutoPull.statusKey(
                    ClientPoolTiers.isPullEnabled(accessor.getPosition()),
                    accessor.getWorld().isBlockPowered(accessor.getPosition()))));
        }
    }

    /** Сервер: строки состояния цветка в данные Jade (JSON-компоненты). */
    private static class FlowerData implements mcp.mobius.waila.api.IServerDataProvider<net.minecraft.tileentity.TileEntity> {

        static final String KEY = "manaessencebridge_status";

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundNBT data, net.minecraft.entity.player.ServerPlayerEntity player,
                                     net.minecraft.world.World world, net.minecraft.tileentity.TileEntity te) {
            if (te instanceof com.frostfirebloom.manaessencebridge.FlowerStatus) {
                net.minecraft.nbt.ListNBT list = new net.minecraft.nbt.ListNBT();
                for (ITextComponent line : ((com.frostfirebloom.manaessencebridge.FlowerStatus) te).statusLines()) {
                    list.add(net.minecraft.nbt.StringNBT.valueOf(ITextComponent.Serializer.toJson(line)));
                }
                data.put(KEY, list);
            }
        }
    }
}
