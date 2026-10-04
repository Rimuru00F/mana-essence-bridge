package com.frostfirebloom.manaessencebridge;

import net.minecraft.potion.Effect;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Эффекты мода. */
public final class ModEffects {

    public static final DeferredRegister<Effect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.POTIONS, ManaEssenceBridge.MODID);

    /** «Праздничное настроение» от Праздничного мана-тортика. */
    public static final RegistryObject<Effect> BIRTHDAY_CHEER =
            EFFECTS.register("birthday_cheer", BirthdayCheerEffect::new);

    private ModEffects() {
    }
}
