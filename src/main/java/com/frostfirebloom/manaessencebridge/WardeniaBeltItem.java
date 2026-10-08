package com.frostfirebloom.manaessencebridge;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import top.theillusivec4.curios.api.CuriosApi;
import vazkii.botania.api.mana.ManaItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Пояс Стражении (слот пояса Curios): защита Стражении вокруг игрока. Гасит
 * снаряды враждебных мобов, отталкивает самих мобов, отменяет их взрывы рядом
 * и не даёт вытоптать пашню. Мана - из колец и планшетов, по ценам цветка.
 */
public class WardeniaBeltItem extends Item {

    /** Снаряды гасятся на таком расстоянии от игрока. */
    private static final double SHOT_RANGE = 4;
    /** Мобы ближе этого отталкиваются. */
    private static final double PUSH_RANGE = 2;
    /** Взрыв моба ближе этого отменяется. */
    private static final double BLAST_RANGE = 6;

    public WardeniaBeltItem() {
        super(new Item.Properties().maxStackSize(1).rarity(Rarity.UNCOMMON).group(ItemGroup.MISC));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.belt_shots").mergeStyle(TextFormatting.GREEN));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.belt_push").mergeStyle(TextFormatting.GREEN));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.belt_blast").mergeStyle(TextFormatting.GREEN));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.belt_trample").mergeStyle(TextFormatting.GREEN));
        tooltip.add(new TranslationTextComponent("tooltip.manaessencebridge.belt_mana").mergeStyle(TextFormatting.DARK_GRAY));
    }

    /** Надетый пояс или пустой стак. */
    private static ItemStack find(PlayerEntity player) {
        return CuriosApi.getCuriosHelper().findEquippedCurio(ModItems.WARDENIA_BELT.get(), player)
                .map(ImmutableTriple::getRight)
                .orElse(ItemStack.EMPTY);
    }

    /** Списывает ману с колец и планшетов; в творческом режиме бесплатно. */
    private static boolean pay(ItemStack belt, PlayerEntity player, int cost) {
        return cost <= 0 || player.abilities.isCreativeMode
                || ManaItemHandler.instance().requestManaExact(belt, player, cost, true);
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        PlayerEntity player = event.player;
        if (event.phase != TickEvent.Phase.END || !(player.world instanceof ServerWorld) || player.isSpectator()) {
            return;
        }
        ItemStack belt = find(player);
        if (belt.isEmpty()) {
            return;
        }
        ServerWorld world = (ServerWorld) player.world;
        // снаряды - каждый тик: стрела пролетает несколько блоков за тик
        stopProjectiles(world, player, belt);
        if (player.ticksExisted % 4 == 0) {
            pushMobs(world, player, belt);
        }
    }

    private static void stopProjectiles(ServerWorld world, PlayerEntity player, ItemStack belt) {
        for (ProjectileEntity shot : world.getEntitiesWithinAABB(ProjectileEntity.class, player.getBoundingBox().grow(SHOT_RANGE),
                e -> e.isAlive() && e.getShooter() instanceof IMob)) {
            if (!pay(belt, player, BridgeConfig.wardeniaProjectileCost())) {
                return;
            }
            world.spawnParticle(ParticleTypes.POOF, shot.getPosX(), shot.getPosY(), shot.getPosZ(), 6, 0.1, 0.1, 0.1, 0.02);
            shot.remove();
        }
    }

    /** Отталкивает враждебных мобов без урона; боссов не трогает. */
    private static void pushMobs(ServerWorld world, PlayerEntity player, ItemStack belt) {
        for (LivingEntity mob : world.getEntitiesWithinAABB(LivingEntity.class,
                player.getBoundingBox().grow(PUSH_RANGE, 1, PUSH_RANGE),
                e -> e instanceof IMob && e.isAlive() && e.canChangeDimension())) {
            if (!pay(belt, player, BridgeConfig.wardeniaPushCost())) {
                return;
            }
            Vector3d away = new Vector3d(mob.getPosX() - player.getPosX(), 0, mob.getPosZ() - player.getPosZ());
            away = away.lengthSquared() < 1.0E-4 ? new Vector3d(1, 0, 0) : away.normalize();
            mob.setMotion(away.x * 2.0, 0.35, away.z * 2.0);
            mob.velocityChanged = true;
            world.spawnParticle(ParticleTypes.CLOUD, mob.getPosX(), mob.getPosY() + 0.5, mob.getPosZ(), 4, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** Взрыв моба (крипер, огненный шар гаста...) рядом с поясом отменяется. Свой динамит - нет. */
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (!(event.getWorld() instanceof ServerWorld) || !hostile(event.getExplosion())) {
            return;
        }
        ServerWorld world = (ServerWorld) event.getWorld();
        Vector3d at = event.getExplosion().getPosition();
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.getDistanceSq(at) > BLAST_RANGE * BLAST_RANGE) {
                continue;
            }
            ItemStack belt = find(player);
            if (!belt.isEmpty() && pay(belt, player, BridgeConfig.wardeniaExplosionCost())) {
                event.setCanceled(true);
                world.spawnParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 0.8, 0.8, 0.8, 0.05);
                world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 0.8F);
                return;
            }
        }
    }

    private static boolean hostile(Explosion explosion) {
        Entity source = explosion.getExploder();
        if (source instanceof ProjectileEntity) {
            source = ((ProjectileEntity) source).getShooter();
        }
        return source instanceof IMob;
    }

    /** В поясе пашню не вытоптать. */
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof PlayerEntity && !find((PlayerEntity) event.getEntity()).isEmpty()) {
            event.setCanceled(true);
        }
    }
}
