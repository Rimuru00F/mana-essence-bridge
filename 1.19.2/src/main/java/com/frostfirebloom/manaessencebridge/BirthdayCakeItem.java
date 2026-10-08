package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import vazkii.botania.api.mana.ManaPool;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Праздничный мана-тортик - подарок на день рождения автора мода, 17 октября
 * (раздаёт CakeDay, скрафтить нельзя). Ставится как ванильный торт
 * (BirthdayCakeBlock) и съедается за 7 кусков: каждый кусок - щедрые эффекты,
 * мана в ближайший пул (чанк торта и соседние), фейерверк и скрытое достижение
 * «С днём рождения, Римуру!». Угостить можно и друзей.
 */
public class BirthdayCakeItem extends BlockItem {

    /** Мана за один кусок: весь торт - около 500 000, как полный мана-планшет. */
    private static final int MANA_PER_SLICE = 72_000;

    /** Где торт ищет пулы: его чанк и столько чанков вокруг (1 = квадрат 3x3). */
    private static final int CHUNK_RADIUS = 1;

    public BirthdayCakeItem(Block block) {
        super(block, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    /** Кусок торта, стоящего на cakePos, съеден. */
    public static void celebrate(ServerPlayer player, BlockPos cakePos) {
        // Съел несколько кусков подряд - время эффектов складывается
        extend(player, MobEffects.REGENERATION, 20 * 60, 1);
        extend(player, MobEffects.ABSORPTION, 20 * 120, 1);
        extend(player, MobEffects.DIG_SPEED, 20 * 300, 1);
        extend(player, MobEffects.LUCK, 20 * 600, 0);
        extend(player, ModEffects.BIRTHDAY_CHEER.get(), 20 * 600, 0);
        // Кусок сытнее ванильного: ещё 2 единицы голода и побольше насыщения
        player.getFoodData().eat(2, 0.5F);

        ServerLevel server = player.getLevel();
        int[] filled = fillPools(server, cakePos, MANA_PER_SLICE);
        int given = filled[0];
        if (given > 0) {
            player.displayClientMessage(Component.translatable("message.manaessencebridge.cake_mana",
                    CatalystItem.format(given)).withStyle(ChatFormatting.AQUA), true);
        } else {
            player.displayClientMessage(Component.translatable(filled[1] == 0
                    ? "message.manaessencebridge.cake_no_pool" : "message.manaessencebridge.cake_pools_full")
                    .withStyle(ChatFormatting.GRAY), true);
        }

        server.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.2, player.getZ(),
                40, 0.6, 0.6, 0.6, 0.15);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.7, 0.5, 0.7, 0.0);
        server.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8F, 1.0F);
        server.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
        PoolThroughput.award(player, "cake_day");
    }

    /**
     * Мана в пулы чанка, где стоит торт, и восьми соседних (3x3 чанка):
     * сначала в ближайший к торту, остаток - в следующий. Незагруженные
     * чанки пропускаем. Возвращает {сколько маны разошлось, сколько пулов нашлось}.
     */
    private static int[] fillPools(ServerLevel level, BlockPos cakePos, int amount) {
        List<BlockEntity> pools = new ArrayList<>();
        int cx = cakePos.getX() >> 4;
        int cz = cakePos.getZ() >> 4;
        for (int x = cx - CHUNK_RADIUS; x <= cx + CHUNK_RADIUS; x++) {
            for (int z = cz - CHUNK_RADIUS; z <= cz + CHUNK_RADIUS; z++) {
                if (!level.hasChunk(x, z)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
                    if (be instanceof ManaPool && !be.isRemoved()) {
                        pools.add(be);
                    }
                }
            }
        }
        pools.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(cakePos)));
        int left = amount;
        for (BlockEntity be : pools) {
            ManaPool pool = (ManaPool) be;
            int room = PoolCapacity.maxMana(be) - pool.getCurrentMana();
            if (room <= 0) {
                continue;
            }
            int add = Math.min(room, left);
            pool.receiveMana(add);
            be.setChanged();
            BlockState state = level.getBlockState(be.getBlockPos());
            level.sendBlockUpdated(be.getBlockPos(), state, state, 3);
            PoolEffects.beam(level, cakePos, be.getBlockPos(), EssenceTier.INFERIUM);
            left -= add;
            if (left <= 0) {
                break;
            }
        }
        return new int[]{amount - left, pools.size()};
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.manaessencebridge.birthday_cake").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.manaessencebridge.birthday_cake_what").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Эффект на duration тиков поверх уже идущего такого же: остаток
     * прибавляется, но не больше целого торта (7 кусков). Более сильный чужой
     * эффект (например, Спешка III от маяка) не трогаем.
     */
    private static void extend(ServerPlayer player, net.minecraft.world.effect.MobEffect effect, int duration, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        int total = duration;
        if (current != null && current.getAmplifier() <= amplifier) {
            total = Math.min(duration * 7, duration + current.getDuration());
        }
        player.addEffect(new MobEffectInstance(effect, total, amplifier));
    }
}
