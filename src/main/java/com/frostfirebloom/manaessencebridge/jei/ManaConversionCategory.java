package com.frostfirebloom.manaessencebridge.jei;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.ManaEssenceBridge;
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
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;

/**
 * Вкладка JEI с курсом обмена. Фон рисуем пустым drawable, без своей
 * текстуры - всё содержимое это два слота и три строки текста.
 */
public class ManaConversionCategory implements IRecipeCategory<ManaConversionRecipe> {

    public static final ResourceLocation UID = new ResourceLocation(ManaEssenceBridge.MODID, "mana_conversion");

    private static final int WIDTH = 160;
    private static final int HEIGHT = 48;

    private final IDrawable background;
    private final IDrawable icon;

    public ManaConversionCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(
                new net.minecraft.item.ItemStack(
                        com.frostfirebloom.manaessencebridge.ModItems
                                .getCatalyst(com.frostfirebloom.manaessencebridge.EssenceTier.INFERIUM).get()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public Class<? extends ManaConversionRecipe> getRecipeClass() {
        return ManaConversionRecipe.class;
    }

    @Override
    public String getTitle() {
        return I18n.format("jei.manaessencebridge.category");
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
    public void setIngredients(ManaConversionRecipe recipe, IIngredients ingredients) {
        ingredients.setInputs(VanillaTypes.ITEM, Arrays.asList(recipe.getEssence(), recipe.getCatalyst()));
        ingredients.setOutput(VanillaTypes.ITEM, recipe.getEssence());
    }

    @Override
    public void setRecipe(IRecipeLayout layout, ManaConversionRecipe recipe, IIngredients ingredients) {
        layout.getItemStacks().init(0, true, 4, 14);
        layout.getItemStacks().set(0, recipe.getEssence());

        layout.getItemStacks().init(1, true, 4, 30);
        layout.getItemStacks().set(1, recipe.getCatalyst());
    }

    @Override
    public void draw(ManaConversionRecipe recipe, MatrixStack matrixStack, double mouseX, double mouseY) {
        FontRenderer font = Minecraft.getInstance().fontRenderer;

        font.drawString(matrixStack,
                I18n.format("jei.manaessencebridge.sell",
                        CatalystItem.format(recipe.getTier().getManaPerEssence())),
                26, 4, 0xFF404040);
        font.drawString(matrixStack,
                I18n.format("jei.manaessencebridge.buy",
                        CatalystItem.format(recipe.getTier().getManaCost())),
                26, 18, 0xFF404040);
        font.drawString(matrixStack,
                I18n.format("jei.manaessencebridge.requires", recipe.getTier().getLevel()),
                26, 34, 0xFF808080);
    }
}
