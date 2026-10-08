package com.frostfirebloom.manaessencebridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
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
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).tab(net.minecraft.world.item.CreativeModeTab.TAB_MISC));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(new net.minecraft.network.chat.TranslatableComponent("tooltip.manaessencebridge.belt_shots").withStyle(ChatFormatting.GREEN));
        tooltip.add(new net.minecraft.network.chat.TranslatableComponent("tooltip.manaessencebridge.belt_push").withStyle(ChatFormatting.GREEN));
        tooltip.add(new net.minecraft.network.chat.TranslatableComponent("tooltip.manaessencebridge.belt_blast").withStyle(ChatFormatting.GREEN));
        tooltip.add(new net.minecraft.network.chat.TranslatableComponent("tooltip.manaessencebridge.belt_trample").withStyle(ChatFormatting.GREEN));
        tooltip.add(new net.minecraft.network.chat.TranslatableComponent("tooltip.manaessencebridge.belt_mana").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Надетый пояс или пустой стак. */
    private static ItemStack find(Player player) {
        return CuriosApi.getCuriosHelper().findFirstCurio(player, ModItems.WARDENIA_BELT.get())
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    /** Списывает ману с колец и планшетов; в творческом режиме бесплатно. */
    private static boolean pay(ItemStack belt, Player player, int cost) {
        return cost <= 0 || player.getAbilities().instabuild
                || ManaItemHandler.instance().requestManaExact(belt, player, cost, true);
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || !(player.level instanceof ServerLevel level) || player.isSpectator()) {
            return;
        }
        ItemStack belt = find(player);
        if (belt.isEmpty()) {
            return;
        }
        // снаряды - каждый тик: стрела пролетает несколько блоков за тик
        stopProjectiles(level, player, belt);
        if (player.tickCount % 4 == 0) {
            pushMobs(level, player, belt);
        }
    }

    private static void stopProjectiles(ServerLevel level, Player player, ItemStack belt) {
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(SHOT_RANGE),
                e -> e.isAlive() && e.getOwner() instanceof Enemy)) {
            if (!pay(belt, player, BridgeConfig.wardeniaProjectileCost())) {
                return;
            }
            level.sendParticles(ParticleTypes.POOF, shot.getX(), shot.getY(), shot.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            shot.discard();
        }
    }

    /** Отталкивает враждебных мобов без урона; боссов не трогает. */
    private static void pushMobs(ServerLevel level, Player player, ItemStack belt) {
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(PUSH_RANGE, 1, PUSH_RANGE),
                e -> e instanceof Enemy && e.isAlive() && e.canChangeDimensions())) {
            if (!pay(belt, player, BridgeConfig.wardeniaPushCost())) {
                return;
            }
            Vec3 away = new Vec3(mob.getX() - player.getX(), 0, mob.getZ() - player.getZ());
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            mob.setDeltaMovement(away.x * 2.0, 0.35, away.z * 2.0);
            mob.hurtMarked = true;
            level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** Взрыв моба (крипер, огненный шар гаста...) рядом с поясом отменяется. Свой динамит - нет. */
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (!(event.getWorld() instanceof ServerLevel level) || !hostile(event.getExplosion())) {
            return;
        }
        Vec3 at = event.getExplosion().getPosition();
        for (Player player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(at) > BLAST_RANGE * BLAST_RANGE) {
                continue;
            }
            ItemStack belt = find(player);
            if (!belt.isEmpty() && pay(belt, player, BridgeConfig.wardeniaExplosionCost())) {
                event.setCanceled(true);
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 0.8, 0.8, 0.8, 0.05);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
                return;
            }
        }
    }

    private static boolean hostile(Explosion explosion) {
        Entity source = explosion.getExploder();
        if (source instanceof Projectile shot) {
            source = shot.getOwner();
        }
        return source instanceof Enemy;
    }

    /** В поясе пашню не вытоптать. */
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Player player && !find(player).isEmpty()) {
            event.setCanceled(true);
        }
    }
}
