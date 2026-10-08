package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Эффекты мода. */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ManaEssenceBridge.MODID);

    /** «Праздничное настроение» от Праздничного мана-тортика. */
    public static final RegistryObject<MobEffect> BIRTHDAY_CHEER =
            EFFECTS.register("birthday_cheer", BirthdayCheerEffect::new);

    private ModEffects() {
    }
}
