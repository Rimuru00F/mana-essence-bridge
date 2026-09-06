package com.frostfirebloom.manaessencebridge.jei;

import com.frostfirebloom.manaessencebridge.EssenceTier;
import net.minecraft.world.item.ItemStack;

/**
 * Одна строка в JEI: эссенция такого-то тира <-> столько-то маны.
 * Не настоящий IRecipe, а простой держатель данных для отрисовки.
 */
public class ManaConversionRecipe {

    private final EssenceTier tier;
    private final ItemStack essence;
    private final ItemStack catalyst;

    public ManaConversionRecipe(EssenceTier tier, ItemStack essence, ItemStack catalyst) {
        this.tier = tier;
        this.essence = essence;
        this.catalyst = catalyst;
    }

    public EssenceTier getTier() {
        return tier;
    }

    public ItemStack getEssence() {
        return essence;
    }

    public ItemStack getCatalyst() {
        return catalyst;
    }
}
