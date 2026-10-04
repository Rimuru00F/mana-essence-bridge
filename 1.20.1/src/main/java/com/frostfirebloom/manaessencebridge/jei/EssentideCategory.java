package com.frostfirebloom.manaessencebridge.jei;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.frostfirebloom.manaessencebridge.ModItems;
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
 * Вкладка JEI «Что ест Эссентида»: каждая ресурсная эссенция Mystical
 * Agriculture, тир её культуры и сколько маны за неё даст цветок.
 */
public class EssentideCategory implements IRecipeCategory<EssentideRecipe> {

    public static final RecipeType<EssentideRecipe> TYPE =
            RecipeType.create(ManaEssenceBridge.MODID, "essentide", EssentideRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;

    public EssentideCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 26);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.ESSENTIDE.get()));
    }

    @Override
    public RecipeType<EssentideRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.manaessencebridge.essentide");
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
    public void setRecipe(IRecipeLayoutBuilder builder, EssentideRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 5).addItemStack(recipe.getEssence());
        builder.addSlot(RecipeIngredientRole.CATALYST, 129, 5).addItemStack(new ItemStack(ModItems.ESSENTIDE.get()));
    }

    @Override
    public void draw(EssentideRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, I18n.get("jei.manaessencebridge.essentide_mana", CatalystItem.format(recipe.getMana())),
                26, 4, 0xFF404040, false);
        graphics.drawString(font, I18n.get("jei.manaessencebridge.essentide_tier", recipe.getTier()),
                26, 15, 0xFF808080, false);
    }
}
