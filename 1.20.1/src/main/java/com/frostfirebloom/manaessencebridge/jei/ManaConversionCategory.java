package com.frostfirebloom.manaessencebridge.jei;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.frostfirebloom.manaessencebridge.ModItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Вкладка JEI с курсом обмена.
 *
 * В JEI 15 категория объявляется через RecipeType, слоты собираются
 * IRecipeLayoutBuilder, а рисование идёт в GuiGraphics - от версии
 * для 1.16.5 не осталось почти ничего.
 */
public class ManaConversionCategory implements IRecipeCategory<ManaConversionRecipe> {

    public static final RecipeType<ManaConversionRecipe> TYPE =
            RecipeType.create(ManaEssenceBridge.MODID, "mana_conversion", ManaConversionRecipe.class);

    private static final int WIDTH = 160;
    private static final int HEIGHT = 48;

    private final IDrawable background;
    private final IDrawable icon;

    public ManaConversionCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(
                new ItemStack(ModItems.getCatalyst(EssenceTier.INFERIUM).get()));
    }

    @Override
    public RecipeType<ManaConversionRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.manaessencebridge.category");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ManaConversionRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 5, 15)
                .addItemStack(recipe.getEssence());
        builder.addSlot(RecipeIngredientRole.CATALYST, 5, 31)
                .addItemStack(recipe.getCatalyst());

        // Обмен идёт в обе стороны, поэтому та же эссенция стоит и на выходе.
        // Заодно категория становится находимой по клавише "как скрафтить",
        // а не только через "где используется".
        builder.addSlot(RecipeIngredientRole.OUTPUT, 139, 15)
                .addItemStack(recipe.getEssence());
    }

    @Override
    public void draw(ManaConversionRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        graphics.drawString(font,
                I18n.get("jei.manaessencebridge.sell",
                        CatalystItem.format(recipe.getTier().getManaPerEssence())),
                26, 4, 0xFF404040, false);
        graphics.drawString(font,
                I18n.get("jei.manaessencebridge.buy",
                        CatalystItem.format(recipe.getTier().getManaCost())),
                26, 18, 0xFF404040, false);
        graphics.drawString(font,
                I18n.get("jei.manaessencebridge.requires", recipe.getTier().getLevel()),
                26, 34, 0xFF808080, false);
    }
}
