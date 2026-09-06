package com.frostfirebloom.manaessencebridge.jei;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.frostfirebloom.manaessencebridge.ModItems;
import com.frostfirebloom.manaessencebridge.ProgressGate;
import com.frostfirebloom.manaessencebridge.client.ClientGates;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Показывает курс обмена в JEI и прячет катализаторы тех тиров,
 * до которых игрок ещё не дошёл по прогрессии Botania.
 *
 * Класс грузится только когда JEI реально стоит в сборке (его ищет сам JEI
 * по аннотации), поэтому зависимость остаётся compileOnly.
 */
@JeiPlugin
public class BridgeJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            new ResourceLocation(ManaEssenceBridge.MODID, "jei_plugin");

    private static IJeiRuntime runtime;

    /** Тиры, которые сейчас скрыты - чтобы не прятать и не показывать дважды. */
    private static final Set<Integer> hidden = new HashSet<>();

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new ManaConversionCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ManaConversionRecipe> recipes = new ArrayList<>();

        for (EssenceTier tier : EssenceTier.values()) {
            if (!tier.isEnabled()) {
                continue;
            }
            Item essence = tier.getEssenceItem();
            if (essence == null) {
                continue; // Mystical Agriculture не установлена
            }
            recipes.add(new ManaConversionRecipe(tier,
                    new ItemStack(essence),
                    new ItemStack(ModItems.getCatalyst(tier).get())));
        }

        registration.addRecipes(ManaConversionCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (EssenceTier tier : EssenceTier.values()) {
            if (tier.isEnabled()) {
                registration.addRecipeCatalyst(
                        new ItemStack(ModItems.getCatalyst(tier).get()), ManaConversionCategory.TYPE);
            }
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        ClientGates.setListener(BridgeJeiPlugin::refreshHidden);
        refreshHidden();
    }

    /**
     * Прячем катализаторы запертых тиров и возвращаем их, когда гейт открылся.
     */
    public static void refreshHidden() {
        if (runtime == null || !BridgeConfig.hideLockedInJei()) {
            return;
        }

        for (EssenceTier tier : EssenceTier.values()) {
            if (ProgressGate.forTier(tier.getLevel()) == null) {
                continue; // тир без гейта виден всегда
            }

            boolean locked = !ClientGates.isTierUnlocked(tier.getLevel());
            if (locked == hidden.contains(tier.getLevel())) {
                continue;
            }

            ItemStack stack = new ItemStack(ModItems.getCatalyst(tier).get());
            try {
                if (locked) {
                    runtime.getIngredientManager()
                            .removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, Collections.singletonList(stack));
                    hidden.add(tier.getLevel());
                } else {
                    runtime.getIngredientManager()
                            .addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, Collections.singletonList(stack));
                    hidden.remove(tier.getLevel());
                }
            } catch (RuntimeException e) {
                ManaEssenceBridge.LOGGER.warn("Failed to update catalyst visibility in JEI", e);
            }
        }
    }
}
