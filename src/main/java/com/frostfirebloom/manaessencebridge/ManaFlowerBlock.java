package com.frostfirebloom.manaessencebridge;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BushBlock;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import vazkii.botania.api.subtile.TileEntitySpecialFlower;
import vazkii.botania.api.wand.IWandHUD;
import vazkii.botania.api.wand.IWandable;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Блок функционального цветка мода.
 *
 * Свой, на основе ванильного куста: блок цветка у Botania - внутренний класс,
 * а мы держимся публичного API. Вся логика живёт в блок-сущности - наследнике
 * TileEntityFunctionalFlower из API, которая сама находит пул, тянет из него
 * ману, привязывается Жезлом леса и слушает редстоун.
 *
 * В 1.16.5 Жезол леса проверяет IWandable на самом блоке, а HUD ищет через
 * IWandHUD тоже на блоке - оба передаём блок-сущности, как делает Botania.
 */
public class ManaFlowerBlock extends BushBlock implements IWandable, IWandHUD {

    private static final VoxelShape SHAPE = makeCuboidShape(4.8, 0, 4.8, 11.2, 12.8, 11.2);

    private final Supplier<? extends TileEntity> factory;
    private final String[] tooltip;

    public ManaFlowerBlock(Supplier<? extends TileEntity> factory, String... tooltip) {
        super(AbstractBlock.Properties.create(Material.PLANTS)
                .doesNotBlockMovement()
                .zeroHardnessAndResistance()
                .sound(SoundType.PLANT));
        this.factory = factory;
        this.tooltip = tooltip;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return factory.get();
    }

    /** Как у цветков Botania: при посадке цветок сразу ищет ближайший пул. */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySpecialFlower) {
            ((TileEntitySpecialFlower) te).onBlockPlacedBy(world, pos, state, placer, stack);
        }
        if (placer instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            // «Ферма на цветках»: у достижения по критерию на каждый цветок мода
            String flower = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(this).getPath();
            PoolThroughput.award((net.minecraft.entity.player.ServerPlayerEntity) placer, "auto_farm", flower);
            // «Садовник Botania»: по критерию на каждый из восьми цветков мода
            PoolThroughput.award((net.minecraft.entity.player.ServerPlayerEntity) placer, "gardener", flower);
        }
    }

    @Override
    public boolean onUsedByWand(PlayerEntity player, ItemStack stack, World world, BlockPos pos, Direction side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileEntitySpecialFlower && ((TileEntitySpecialFlower) te).onWanded(player, stack);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void renderHUD(MatrixStack matrix, Minecraft mc, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySpecialFlower) {
            ((TileEntitySpecialFlower) te).renderHUD(matrix, mc);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable IBlockReader world, List<ITextComponent> lines, ITooltipFlag flag) {
        for (int i = 0; i < tooltip.length; i++) {
            lines.add(new TranslationTextComponent(tooltip[i])
                    .mergeStyle(i == 0 ? TextFormatting.LIGHT_PURPLE : TextFormatting.GRAY));
        }
    }
}
