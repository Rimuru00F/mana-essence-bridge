package com.frostfirebloom.manaessencebridge;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.item.DyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import vazkii.botania.api.mana.IManaPool;

import java.util.Collection;
import java.util.List;

/**
 * /manabridge - команда для операторов (уровень прав 2):
 *   pools &lt;игрок&gt;           - его прокачанные пулы (из реестра Мана-гроссбуха);
 *   settier &lt;x y z&gt; &lt;0-5&gt; - выставить или снять тир пула со всем, что делает катализатор;
 *   setowner &lt;x y z&gt; &lt;игрок&gt; - сменить хозяина пула.
 */
public final class BridgeCommand {

    private BridgeCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("manabridge")
                .requires(source -> source.hasPermissionLevel(2))
                .then(Commands.literal("pools")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(BridgeCommand::pools)))
                .then(Commands.literal("settier")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 6))
                                        .executes(BridgeCommand::setTier))))
                .then(Commands.literal("setowner")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(BridgeCommand::setOwner)))));
    }

    private static int pools(CommandContext<CommandSource> ctx) throws CommandSyntaxException {
        CommandSource source = ctx.getSource();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        int total = 0;
        for (GameProfile profile : profiles) {
            List<PoolLedger.Entry> pools = PoolLedger.listFor(source.getServer(), profile.getId());
            total += pools.size();
            send(source, text("command.manaessencebridge.pools_header", TextFormatting.GOLD, profile.getName(), pools.size()));
            for (PoolLedger.Entry e : pools) {
                EssenceTier tier = EssenceTier.byLevel(e.tier);
                send(source, text("command.manaessencebridge.pools_line", TextFormatting.GRAY,
                        e.tier, tier == null ? new StringTextComponent("?") : tier.getDisplayName(),
                        e.blockPos().getX(), e.blockPos().getY(), e.blockPos().getZ(), e.dim,
                        CatalystItem.format(e.mana), CatalystItem.format(e.maxMana), CatalystItem.format(e.processed)));
            }
        }
        return total;
    }

    private static int setTier(CommandContext<CommandSource> ctx) throws CommandSyntaxException {
        CommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        int tier = IntegerArgumentType.getInteger(ctx, "tier");
        TileEntity te = world.getTileEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!(te instanceof IManaPool) || cap == null) {
            source.sendErrorMessage(new TranslationTextComponent("command.manaessencebridge.not_pool"));
            return 0;
        }
        cap.setTier(tier);
        if (tier == 0) {
            cap.setPullEnabled(false);
        }
        EssenceTier t = EssenceTier.byLevel(tier);
        ((IManaPool) te).setColor(t == null ? DyeColor.WHITE : t.getPoolColor());
        PoolCapacity.apply(world, pos, te, cap);
        te.markDirty();
        BlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
        ModNetwork.syncToTracking(world, pos, cap);
        if (tier > 0) {
            PoolAutoPull.track(world, pos);
        } else {
            PoolAutoPull.untrack(world, pos);
        }
        send(source, text("command.manaessencebridge.tier_set", TextFormatting.AQUA, pos.getX(), pos.getY(), pos.getZ(), tier));
        return 1;
    }

    private static int setOwner(CommandContext<CommandSource> ctx) throws CommandSyntaxException {
        CommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        TileEntity te = world.getTileEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!(te instanceof IManaPool) || cap == null || profiles.size() != 1) {
            source.sendErrorMessage(new TranslationTextComponent("command.manaessencebridge.not_pool"));
            return 0;
        }
        GameProfile profile = profiles.iterator().next();
        cap.setOwner(profile.getId());
        te.markDirty();
        ModNetwork.syncToTracking(world, pos, cap);
        send(source, text("command.manaessencebridge.owner_set", TextFormatting.AQUA,
                pos.getX(), pos.getY(), pos.getZ(), profile.getName()));
        return 1;
    }

    private static void send(CommandSource source, ITextComponent message) {
        source.sendFeedback(message, false);
    }

    private static IFormattableTextComponent text(String key, TextFormatting color, Object... args) {
        return new TranslationTextComponent(key, args).mergeStyle(color);
    }
}
