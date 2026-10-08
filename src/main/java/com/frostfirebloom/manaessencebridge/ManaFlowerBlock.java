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
    private static final net.minecraft.util.ResourceLocation RED_STRING_RELAY =
            new net.minecraft.util.ResourceLocation("botania", "red_string_relay");

    public ManaFlowerBlock(Supplier<? extends TileEntity> factory, String... tooltip) {
        this(AbstractBlock.Properties.create(Material.PLANTS)
                .doesNotBlockMovement()
                .zeroHardnessAndResistance()
                .sound(SoundType.PLANT), factory, tooltip);
    }

    /** Для парящего варианта - со своими свойствами блока. */
    protected ManaFlowerBlock(AbstractBlock.Properties properties, Supplier<? extends TileEntity> factory, String... tooltip) {
        super(properties);
        this.factory = factory;
        this.tooltip = tooltip;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        net.minecraft.util.math.vector.Vector3d offset = state.getOffset(world, pos);
        return SHAPE.withOffset(offset.x, offset.y, offset.z);
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
            // парящий вариант засчитывается за тот же цветок
            String flower = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(this).getPath()
                    .replace("floating_", "");
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

    /** Как у цветков Botania: можно сажать и на Red String Relay. */
    @Override
    protected boolean isValidGround(BlockState state, IBlockReader world, BlockPos pos) {
        return RED_STRING_RELAY.equals(state.getBlock().getRegistryName()) || super.isValidGround(state, world, pos);
    }

    /** Как у цветков Botania: функциональный цветок, выключенный редстоуном, искрит. */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, java.util.Random random) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof vazkii.botania.api.subtile.TileEntityFunctionalFlower) || !random.nextBoolean()) {
            return;
        }
        vazkii.botania.api.subtile.TileEntityFunctionalFlower flower = (vazkii.botania.api.subtile.TileEntityFunctionalFlower) te;
        VoxelShape shape = state.getShape(world, pos);
        if (flower.acceptsRedstone() && flower.redstoneSignal > 0 && !shape.isEmpty()) {
            net.minecraft.util.math.AxisAlignedBB box = shape.getBoundingBox();
            world.addParticle(net.minecraft.particles.RedstoneParticleData.REDSTONE_DUST,
                    pos.getX() + box.minX + random.nextDouble() * (box.maxX - box.minX),
                    pos.getY() + box.minY + random.nextDouble() * (box.maxY - box.minY),
                    pos.getZ() + box.minZ + random.nextDouble() * (box.maxZ - box.minZ), 0, 0, 0);
        }
    }

    /** Как ванильные цветы и цветки Botania - чуть сдвинут в блоке случайным образом. */
    @Override
    public AbstractBlock.OffsetType getOffsetType() {
        return AbstractBlock.OffsetType.XZ;
    }
}
