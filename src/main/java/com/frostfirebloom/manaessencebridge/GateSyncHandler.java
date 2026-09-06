package com.frostfirebloom.manaessencebridge;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Держим клиент в курсе, какие гейты прогрессии уже открыты:
 * при входе в игру и каждый раз, когда игрок получает нужное достижение.
 * По этой маске JEI прячет катализаторы недоступных тиров.
 */
public class GateSyncHandler {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            sync((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        // Пересылаем маску только когда получено именно "наше" достижение,
        // а не на каждый чих - их за игру выдаются сотни.
        if (ProgressGate.isGateAdvancement(event.getAdvancement().getId())) {
            sync((ServerPlayerEntity) event.getPlayer());
        }
    }

    private static void sync(ServerPlayerEntity player) {
        ModNetwork.sendGates(player, ProgressGate.maskFor(player));
    }
}
