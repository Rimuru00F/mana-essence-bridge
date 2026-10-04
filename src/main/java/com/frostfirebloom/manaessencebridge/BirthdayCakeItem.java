package com.frostfirebloom.manaessencebridge;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import vazkii.botania.api.mana.IManaPool;

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
        super(block, new Item.Properties().maxStackSize(1).rarity(Rarity.EPIC));
    }

    /** Кусок торта, стоящего на cakePos, съеден. */
    public static void celebrate(ServerPlayerEntity player, BlockPos cakePos) {
        player.addPotionEffect(new EffectInstance(Effects.REGENERATION, 20 * 60, 1));
        player.addPotionEffect(new EffectInstance(Effects.ABSORPTION, 20 * 120, 1));
        player.addPotionEffect(new EffectInstance(Effects.HASTE, 20 * 300, 1));
        player.addPotionEffect(new EffectInstance(Effects.LUCK, 20 * 600, 0));
        player.addPotionEffect(new EffectInstance(ModEffects.BIRTHDAY_CHEER.get(), 20 * 600, 0));

        ServerWorld server = player.getServerWorld();
        int[] filled = fillPools(server, cakePos, MANA_PER_SLICE);
        int given = filled[0];
        if (given > 0) {
            player.sendStatusMessage(new TranslationTextComponent("message.manaessencebridge.cake_mana",
                    CatalystItem.format(given)).mergeStyle(TextFormatting.AQUA), true);
        } else {
            player.sendStatusMessage(new TranslationTextComponent(filled[1] == 0
                    ? "message.manaessencebridge.cake_no_pool" : "message.manaessencebridge.cake_pools_full")
                    .mergeStyle(TextFormatting.GRAY), true);
        }

        server.spawnParticle(ParticleTypes.FIREWORK, player.getPosX(), player.getPosY() + 1.2, player.getPosZ(),
                40, 0.6, 0.6, 0.6, 0.15);
        server.spawnParticle(ParticleTypes.HAPPY_VILLAGER, player.getPosX(), player.getPosY() + 1.0, player.getPosZ(),
                12, 0.7, 0.5, 0.7, 0.0);
        server.playSound(null, player.getPosition(), SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.8F, 1.0F);
        server.playSound(null, player.getPosition(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6F, 1.3F);
        PoolThroughput.award(player, "cake_day");
    }

    /**
     * Мана в пулы чанка, где стоит торт, и восьми соседних (3x3 чанка):
     * сначала в ближайший к торту, остаток - в следующий. Незагруженные
     * чанки пропускаем. Возвращает {сколько маны разошлось, сколько пулов нашлось}.
     */
    private static int[] fillPools(ServerWorld world, BlockPos cakePos, int amount) {
        List<TileEntity> pools = new ArrayList<>();
        int cx = cakePos.getX() >> 4;
        int cz = cakePos.getZ() >> 4;
        for (int x = cx - CHUNK_RADIUS; x <= cx + CHUNK_RADIUS; x++) {
            for (int z = cz - CHUNK_RADIUS; z <= cz + CHUNK_RADIUS; z++) {
                if (!world.getChunkProvider().isChunkLoaded(new ChunkPos(x, z))) {
                    continue;
                }
                for (TileEntity te : world.getChunk(x, z).getTileEntityMap().values()) {
                    if (te instanceof IManaPool && !te.isRemoved()) {
                        pools.add(te);
                    }
                }
            }
        }
        pools.sort(Comparator.comparingDouble(te -> te.getPos().distanceSq(cakePos)));
        int left = amount;
        for (TileEntity te : pools) {
            IManaPool pool = (IManaPool) te;
            int room = PoolCapacity.maxMana(te) - pool.getCurrentMana();
            if (room <= 0) {
                continue;
            }
            int add = Math.min(room, left);
            pool.receiveMana(add);
            te.markDirty();
            BlockState state = world.getBlockState(te.getPos());
            world.notifyBlockUpdate(te.getPos(), state, state, 3);
            PoolEffects.beam(world, cakePos, te.getPos(), EssenceTier.INFERIUM);
            left -= add;
            if (left <= 0) {
                break;
            }
        }
        return new int[]{amount - left, pools.size()};
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.birthday_cake").mergeStyle(TextFormatting.LIGHT_PURPLE));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.birthday_cake_what").mergeStyle(TextFormatting.GRAY));
    }
}
