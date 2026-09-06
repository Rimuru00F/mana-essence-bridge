package com.frostfirebloom.manaessencebridge;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public class ModCapabilities {

    /**
     * В 1.18+ капабилити получают через CapabilityToken, а @CapabilityInject
     * и CapabilityManager.register(Class, IStorage, Callable) удалены.
     * Сериализацию теперь целиком делает ICapabilitySerializable провайдера,
     * отдельный класс Storage больше не нужен.
     */
    public static final Capability<InferiumCatalystCapability> INFERIUM_CATALYST_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<InferiumCatalystCapability>() {
            });

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(InferiumCatalystCapability.class);
    }
}
