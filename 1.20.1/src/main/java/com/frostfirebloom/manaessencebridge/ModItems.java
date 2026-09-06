package com.frostfirebloom.manaessencebridge;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ManaEssenceBridge.MODID);

    /**
     * По катализатору на каждый тир эссенции. Каждый следующий крафтится
     * из предыдущего, так что прокачка пула идёт строго по порядку.
     */
    private static final Map<EssenceTier, RegistryObject<Item>> CATALYSTS = new EnumMap<>(EssenceTier.class);

    static {
        for (EssenceTier tier : EssenceTier.values()) {
            CATALYSTS.put(tier, ITEMS.register(tier.getCatalystRegistryName(), () -> new CatalystItem(tier)));
        }
    }

    public static RegistryObject<Item> getCatalyst(EssenceTier tier) {
        return CATALYSTS.get(tier);
    }

    /**
     * С 1.19.3 предмет не объявляет вкладку сам - его добавляют в существующую
     * вкладку событием. Кладём катализаторы к ингредиентам.
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.INGREDIENTS) {
            return;
        }
        for (EssenceTier tier : EssenceTier.values()) {
            event.accept(CATALYSTS.get(tier).get());
        }
    }
}
