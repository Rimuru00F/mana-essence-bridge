package com.frostfirebloom.manaessencebridge.jei;

import com.frostfirebloom.manaessencebridge.BridgeConfig;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.EssentideBlockEntity;
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
                new ManaConversionCategory(registration.getJeiHelpers().getGuiHelper()),
                new EssentideCategory(registration.getJeiHelpers().getGuiHelper()));
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
        registration.addRecipes(EssentideCategory.TYPE, essentideRecipes());
        // Торт не крафтится - по R/U в JEI показываем, откуда он и что делает.
        registration.addItemStackInfo(new ItemStack(ModItems.BIRTHDAY_CAKE.get()),
                net.minecraft.network.chat.Component.translatable("jei.manaessencebridge.birthday_cake.1"),
                net.minecraft.network.chat.Component.translatable("jei.manaessencebridge.birthday_cake.2"),
                net.minecraft.network.chat.Component.translatable("jei.manaessencebridge.birthday_cake.3"),
                net.minecraft.network.chat.Component.translatable("jei.manaessencebridge.birthday_cake.4"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (EssenceTier tier : EssenceTier.values()) {
            if (tier.isEnabled()) {
                registration.addRecipeCatalyst(
                        new ItemStack(ModItems.getCatalyst(tier).get()), ManaConversionCategory.TYPE);
            }
        }
        // Конденсатор делает ту же работу, что и пул, только в обратную сторону.
        registration.addRecipeCatalyst(new ItemStack(ModItems.ESSENCE_CONDENSER.get()), ManaConversionCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.ESSENTIDE.get()), EssentideCategory.TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // JEI пересобирает список предметов с нуля при каждой перезагрузке
        // ресурсов, то есть всё снова видно. Забываем, что прятали раньше,
        // иначе сверка "уже спрятано" совпадёт и мы ничего не спрячем.
        hidden.clear();
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

    /** Все ресурсные эссенции MA, которые ест Эссентида: по тиру, внутри тира - по имени. */
    private static List<EssentideRecipe> essentideRecipes() {
        List<EssentideRecipe> list = new ArrayList<>();
        for (Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()) {
            int tier = EssentideBlockEntity.tierOf(item);
            if (tier > 0) {
                list.add(new EssentideRecipe(new ItemStack(item), tier, EssentideBlockEntity.manaFor(tier)));
            }
        }
        list.sort(java.util.Comparator.comparingInt(EssentideRecipe::getTier)
                .thenComparing(r -> String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(r.getEssence().getItem()))));
        return list;
    }
}
