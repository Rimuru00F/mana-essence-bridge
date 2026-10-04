package com.frostfirebloom.manaessencebridge.jei;

import net.minecraft.item.ItemStack;

/** Строка вкладки «Что ест Эссентида»: ресурсная эссенция, тир её культуры и мана за штуку. */
public class EssentideRecipe {

    private final ItemStack essence;
    private final int tier;
    private final int mana;

    public EssentideRecipe(ItemStack essence, int tier, int mana) {
        this.essence = essence;
        this.tier = tier;
        this.mana = mana;
    }

    public ItemStack getEssence() {
        return essence;
    }

    public int getTier() {
        return tier;
    }

    public int getMana() {
        return mana;
    }
}
