package com.frostfirebloom.manaessencebridge;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import vazkii.botania.api.mana.ManaPool;

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
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("manabridge")
                .requires(source -> source.hasPermission(2))
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

    private static int pools(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        int total = 0;
        for (GameProfile profile : profiles) {
            List<PoolLedger.Entry> pools = PoolLedger.listFor(source.getServer(), profile.getId());
            total += pools.size();
            send(source, text("command.manaessencebridge.pools_header", ChatFormatting.GOLD, profile.getName(), pools.size()));
            for (PoolLedger.Entry e : pools) {
                EssenceTier tier = EssenceTier.byLevel(e.tier);
                send(source, text("command.manaessencebridge.pools_line", ChatFormatting.GRAY,
                        e.tier, tier == null ? Component.literal("?") : tier.getDisplayName(),
                        e.blockPos().getX(), e.blockPos().getY(), e.blockPos().getZ(), e.dim,
                        CatalystItem.format(e.mana), CatalystItem.format(e.maxMana), CatalystItem.format(e.processed)));
            }
        }
        return total;
    }

    private static int setTier(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        int tier = IntegerArgumentType.getInteger(ctx, "tier");
        BlockEntity te = level.getBlockEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!(te instanceof ManaPool) || cap == null) {
            source.sendFailure(Component.translatable("command.manaessencebridge.not_pool"));
            return 0;
        }
        cap.setTier(tier);
        if (tier == 0) {
            cap.setPullEnabled(false);
        }
        EssenceTier t = EssenceTier.byLevel(tier);
        ((ManaPool) te).setColor(t == null ? java.util.Optional.empty() : java.util.Optional.of(t.getPoolColor()));
        PoolCapacity.apply(level, pos, te, cap);
        te.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
        ModNetwork.syncToTracking(level, pos, cap);
        if (tier > 0) {
            PoolAutoPull.track(level, pos);
        } else {
            PoolAutoPull.untrack(level, pos);
        }
        send(source, text("command.manaessencebridge.tier_set", ChatFormatting.AQUA, pos.getX(), pos.getY(), pos.getZ(), tier));
        return 1;
    }

    private static int setOwner(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        BlockEntity te = level.getBlockEntity(pos);
        InferiumCatalystCapability cap = te == null ? null
                : te.getCapability(ModCapabilities.INFERIUM_CATALYST_CAPABILITY).orElse(null);
        if (!(te instanceof ManaPool) || cap == null || profiles.size() != 1) {
            source.sendFailure(Component.translatable("command.manaessencebridge.not_pool"));
            return 0;
        }
        GameProfile profile = profiles.iterator().next();
        cap.setOwner(profile.getId());
        te.setChanged();
        ModNetwork.syncToTracking(level, pos, cap);
        send(source, text("command.manaessencebridge.owner_set", ChatFormatting.AQUA,
                pos.getX(), pos.getY(), pos.getZ(), profile.getName()));
        return 1;
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(message, false);
    }

    private static MutableComponent text(String key, ChatFormatting color, Object... args) {
        return Component.translatable(key, args).withStyle(color);
    }
}
