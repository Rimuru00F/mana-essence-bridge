package com.frostfirebloom.manaessencebridge;

import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntNBT;
import net.minecraft.nbt.NumberNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class ModCapabilities {

    @CapabilityInject(InferiumCatalystCapability.class)
    public static Capability<InferiumCatalystCapability> INFERIUM_CATALYST_CAPABILITY = null;

    /**
     * В Forge 1.16.5 капабилити регистрируются через CapabilityManager
     * во время FMLCommonSetupEvent (событие RegisterCapabilitiesEvent появилось
     * только в 1.18+, здесь его ещё нет).
     */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CapabilityManager.INSTANCE.register(
                InferiumCatalystCapability.class,
                new Storage(),
                InferiumCatalystCapability::new));
    }

    /**
     * Сериализация тира пула в NBT.
     * Используется в CapabilityAttachHandler при создании capability provider'а.
     */
    public static class Storage implements Capability.IStorage<InferiumCatalystCapability> {
        @Override
        public INBT writeNBT(Capability<InferiumCatalystCapability> capability, InferiumCatalystCapability instance, Direction side) {
            return IntNBT.valueOf(instance.getTier());
        }

        @Override
        public void readNBT(Capability<InferiumCatalystCapability> capability, InferiumCatalystCapability instance, Direction side, INBT nbt) {
            if (nbt instanceof NumberNBT) {
                instance.setTier(((NumberNBT) nbt).getInt());
            }
        }
    }
}
