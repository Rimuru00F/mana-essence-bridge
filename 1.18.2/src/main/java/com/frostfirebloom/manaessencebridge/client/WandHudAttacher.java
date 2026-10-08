package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.EssenceCondenserBlockEntity;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.frostfirebloom.manaessencebridge.MysticarnationBlockEntity;
import com.frostfirebloom.manaessencebridge.ReaperbloomBlockEntity;
import com.frostfirebloom.manaessencebridge.WardeniaBlockEntity;
import com.frostfirebloom.manaessencebridge.EssentideBlockEntity;
import com.frostfirebloom.manaessencebridge.BrookbellBlockEntity;
import com.frostfirebloom.manaessencebridge.BoltbloomBlockEntity;
import com.frostfirebloom.manaessencebridge.MelodiaBlockEntity;
import com.frostfirebloom.manaessencebridge.BumblebloomBlockEntity;
import vazkii.botania.api.BotaniaForgeClientCapabilities;
import vazkii.botania.api.subtile.TileEntityBindableSpecialFlower;
import vazkii.botania.api.block.IWandHUD;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Botania ищет HUD жезла через клиентскую capability WAND_HUD на блок-сущности.
 * Вешаем её на Конденсатор - так же, как Botania вешает свои.
 */
public class WandHudAttacher {

    private static final ResourceLocation ID = new ResourceLocation(ManaEssenceBridge.MODID, "condenser_wand_hud");
    private static final ResourceLocation FLOWER_ID = new ResourceLocation(ManaEssenceBridge.MODID, "flower_wand_hud");

    @SubscribeEvent
    public void onAttach(AttachCapabilitiesEvent<BlockEntity> event) {
        // Цветкам - стандартный HUD Botania: мана, привязка к пулу
        if (event.getObject() instanceof MysticarnationBlockEntity
                || event.getObject() instanceof ReaperbloomBlockEntity
                || event.getObject() instanceof WardeniaBlockEntity
                || event.getObject() instanceof EssentideBlockEntity
                || event.getObject() instanceof BrookbellBlockEntity
                || event.getObject() instanceof BoltbloomBlockEntity
                || event.getObject() instanceof MelodiaBlockEntity
                || event.getObject() instanceof BumblebloomBlockEntity) {
            // в 1.18.2 у Botania отдельные HUD для функциональных и генерирующих цветков
            IWandHUD flowerHud = event.getObject() instanceof vazkii.botania.api.subtile.TileEntityFunctionalFlower
                    ? new vazkii.botania.api.subtile.TileEntityFunctionalFlower.FunctionalWandHud<>(
                            (vazkii.botania.api.subtile.TileEntityFunctionalFlower) event.getObject())
                    : new vazkii.botania.api.subtile.TileEntityGeneratingFlower.GeneratingWandHud<>(
                            (vazkii.botania.api.subtile.TileEntityGeneratingFlower) event.getObject());
            attach(event, FLOWER_ID, flowerHud);
            return;
        }
        if (!(event.getObject() instanceof EssenceCondenserBlockEntity)) {
            return;
        }
        EssenceCondenserBlockEntity condenser = (EssenceCondenserBlockEntity) event.getObject();
        IWandHUD hud = (graphics, mc) -> CondenserHud.render(graphics, mc, condenser);
        attach(event, ID, hud);
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event, ResourceLocation id, IWandHUD hud) {
        LazyOptional<IWandHUD> optional = LazyOptional.of(() -> hud);
        event.addCapability(id, new ICapabilityProvider() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                return BotaniaForgeClientCapabilities.WAND_HUD.orEmpty(cap, optional);
            }
        });
    }
}
