package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.CondenserHud;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolType;
import vazkii.botania.api.wand.IWandHUD;
import vazkii.botania.api.wand.IWandable;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Конденсатор эссенции: сам выкупает у прокачанного пула эссенцию за ману.
 *
 * Вторая половина моста для автоматики. Эссенция в ману и так идёт сама
 * (воронки, трубы, сифон), а обратно - только ручным кликом. Конденсатор
 * берёт ману из пула по обычной цене выкупа и складывает эссенцию в себя,
 * откуда её забирают воронки и трубы.
 */
/*
 * В 1.16.5 Жезол леса проверяет IWandable на самом блоке и только потом
 * смотрит, умеет ли блок-сущность привязываться. Без IWandable он
 * конденсатор просто не замечает. IWandHUD тоже ищется на блоке.
 */
public class EssenceCondenserBlock extends Block implements IWandable, IWandHUD {

    public EssenceCondenserBlock() {
        super(AbstractBlock.Properties.create(Material.ROCK, MaterialColor.QUARTZ)
                .hardnessAndResistance(2.0F, 10.0F)
                .sound(SoundType.STONE)
                .setRequiresTool()
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0));
    }

    /**
     * Клик жезлом, который не стал привязкой, меняет резерв - запасной путь
     * на случай, если Shift+ПКМ пустой рукой перехватывает другой мод.
     */
    @Override
    public boolean onUsedByWand(PlayerEntity player, ItemStack stack, World world, BlockPos pos, Direction side) {
        if (!world.isRemote && player != null) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof EssenceCondenserBlockEntity) {
                ((EssenceCondenserBlockEntity) te).cycleReserve(player);
            }
        }
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void renderHUD(MatrixStack matrix, Minecraft mc, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof EssenceCondenserBlockEntity) {
            CondenserHud.render(matrix, mc, (EssenceCondenserBlockEntity) te);
        }
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new EssenceCondenserBlockEntity();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockRayTraceResult hit) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResultType.PASS;
        }
        ItemStack held = player.getHeldItem(hand);
        EssenceTier tier = EssenceTier.fromItem(held.getItem());
        // С любым другим предметом - прежде всего с жезлом Botania для привязки -
        // отдаём клик самому предмету.
        if (tier == null && !held.isEmpty()) {
            return ActionResultType.PASS;
        }
        if (world.isRemote) {
            return ActionResultType.SUCCESS;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof EssenceCondenserBlockEntity)) {
            return ActionResultType.PASS;
        }
        EssenceCondenserBlockEntity condenser = (EssenceCondenserBlockEntity) te;
        if (tier != null) {
            condenser.setFilter(player, tier);
        } else if (player.isSneaking()) {
            condenser.cycleReserve(player);
        } else {
            condenser.handOut(player);
        }
        return ActionResultType.CONSUME;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean hasComparatorInputOverride(BlockState state) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getComparatorInputOverride(BlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof EssenceCondenserBlockEntity ? ((EssenceCondenserBlockEntity) te).comparatorSignal() : 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock()) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof EssenceCondenserBlockEntity) {
                ((EssenceCondenserBlockEntity) te).dropContents();
            }
        }
        super.onReplaced(state, world, pos, newState, moving);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable IBlockReader world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.condenser_what").mergeStyle(TextFormatting.LIGHT_PURPLE));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.condenser_how").mergeStyle(TextFormatting.GRAY));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.condenser_bind").mergeStyle(TextFormatting.GRAY));
    }

    /** Достижение «Конденсатор ожил» - тому, кто поставил блок. */
    @Override
    public void onBlockPlacedBy(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                net.minecraft.block.BlockState state,
                                @javax.annotation.Nullable net.minecraft.entity.LivingEntity placer,
                                net.minecraft.item.ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (placer != null && te instanceof EssenceCondenserBlockEntity) {
            ((EssenceCondenserBlockEntity) te).setOwner(placer.getUniqueID());
        }
        if (placer instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            PoolThroughput.award((net.minecraft.entity.player.ServerPlayerEntity) placer, "condenser_set");
        }
    }
}
