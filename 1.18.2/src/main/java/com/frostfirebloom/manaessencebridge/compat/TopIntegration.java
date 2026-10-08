package com.frostfirebloom.manaessencebridge.compat;

import net.minecraft.network.chat.TranslatableComponent;
import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.InferiumCatalystCapability;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.frostfirebloom.manaessencebridge.ModCapabilities;
import com.frostfirebloom.manaessencebridge.PoolAutoPull;
import com.frostfirebloom.manaessencebridge.PoolCapacity;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.InterModComms;
import vazkii.botania.api.mana.IManaPool;

import java.util.function.Function;

/**
 * Показ тира и курса в The One Probe.
 *
 * Провайдеры TOP выполняются на сервере, поэтому тир читается прямо
 * из capability блока - никакой синхронизации не требуется.
 *
 * Весь класс грузится только если TOP реально стоит в сборке: вызов
 * register() спрятан за проверкой ModList в главном классе мода.
 */
public final class TopIntegration {

    private TopIntegration() {
    }

    public static void register() {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", Sender::new);
    }

    /** TOP ждёт функцию, которой передаст своё API при загрузке. */
    public static class Sender implements Function<ITheOneProbe, Void> {
        @Override
        public Void apply(ITheOneProbe probe) {
            probe.registerProvider(new PoolProvider());
            return null;
        }
    }

    private static class PoolProvider implements IProbeInfoProvider {

        @Override
        public ResourceLocation getID() {
            return new ResourceLocation(ManaEssenceBridge.MODID, "mana_pool");
        }

        @Override
        public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player,
                                 Level level, BlockState state, IProbeHitData data) {
            BlockEntity te = level.getBlockEntity(data.getPos());
            if (te == null) {
                return;
            }
            if (te instanceof com.frostfirebloom.manaessencebridge.FlowerStatus) {
                for (Component line : ((com.frostfirebloom.manaessencebridge.FlowerStatus) te).statusLines()) {
                    info.text(line);
                }
                return;
            }
            if (te instanceof EssenceCondenserBlockEntity) {
                for (Component line : ((EssenceCondenserBlockEntity) te).infoLines(level)) {
                    info.text(line);
                }
                return;
            }

            InferiumCatalystCapability cap =
                    te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
            if (cap == null || !cap.isUpgraded()) {
                return;
            }

            EssenceTier tier = EssenceTier.byLevel(cap.getTier());
            if (tier == null) {
                return;
            }

            info.text(new TranslatableComponent("hud.manaessencebridge.title",
                    tier.getLevel(), tier.getDisplayName()));
            info.text(new TranslatableComponent("hud.manaessencebridge.rate",
                    tier.getDisplayName(),
                    CatalystItem.format(tier.getManaPerEssence()),
                    CatalystItem.format(tier.getManaCost())));
            if (te instanceof IManaPool) {
                info.text(new TranslatableComponent("hud.manaessencebridge.stored",
                        CatalystItem.format(((IManaPool) te).getCurrentMana()),
                        CatalystItem.format(PoolCapacity.maxMana(te))));
            }
            info.text(new TranslatableComponent("hud.manaessencebridge.processed",
                    CatalystItem.format(cap.getProcessed())));
            info.text(new TranslatableComponent(PoolAutoPull.statusKey(
                    cap.isPullEnabled(), level.hasNeighborSignal(data.getPos()))));
        }
    }
}
