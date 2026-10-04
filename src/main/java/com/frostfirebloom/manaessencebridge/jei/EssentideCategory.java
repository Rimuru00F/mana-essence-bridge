package com.frostfirebloom.manaessencebridge.jei;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
import com.frostfirebloom.manaessencebridge.ModItems;
import com.mojang.blaze3d.matrix.MatrixStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;

/**
 * Вкладка JEI «Что ест Эссентида»: каждая ресурсная эссенция Mystical
 * Agriculture, тир её культуры и сколько маны за неё даст цветок.
 */
public class EssentideCategory implements IRecipeCategory<EssentideRecipe> {

    public static final ResourceLocation UID = new ResourceLocation(ManaEssenceBridge.MODID, "essentide");

    private final IDrawable background;
    private final IDrawable icon;

    public EssentideCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 26);
        this.icon = guiHelper.createDrawableIngredient(new ItemStack(ModItems.ESSENTIDE.get()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public Class<? extends EssentideRecipe> getRecipeClass() {
        return EssentideRecipe.class;
    }

    @Override
    public String getTitle() {
        return I18n.format("jei.manaessencebridge.essentide");
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
    public void setIngredients(EssentideRecipe recipe, IIngredients ingredients) {
        ingredients.setInputs(VanillaTypes.ITEM, Arrays.asList(recipe.getEssence(), new ItemStack(ModItems.ESSENTIDE.get())));
    }

    @Override
    public void setRecipe(IRecipeLayout layout, EssentideRecipe recipe, IIngredients ingredients) {
        layout.getItemStacks().init(0, true, 3, 4);
        layout.getItemStacks().set(0, recipe.getEssence());
        layout.getItemStacks().init(1, true, 128, 4);
        layout.getItemStacks().set(1, new ItemStack(ModItems.ESSENTIDE.get()));
    }

    @Override
    public void draw(EssentideRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
        FontRenderer font = Minecraft.getInstance().fontRenderer;
        font.drawString(matrixStack, I18n.format("jei.manaessencebridge.essentide_mana", CatalystItem.format(recipe.getMana())),
                26, 4, 0xFF404040);
        font.drawString(matrixStack, I18n.format("jei.manaessencebridge.essentide_tier", recipe.getTier()),
                26, 15, 0xFF808080);
    }
}
