package com.frostfirebloom.manaessencebridge;

import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Оборот пула: сколько маны через него прошло, и достижения за это.
 *
 * Счётчик копится в capability и растёт при каждой конвертации - ручной,
 * воронкой или автозабором из сундука. Достижения выдаются из кода через
 * критерий "minecraft:impossible": для ручной операции - тому, кто кликнул,
 * для автоматики - хозяину пула (кто поставил первый катализатор), если он
 * сейчас в сети. Если хозяина нет, порог никуда не денется: он проверится
 * заново, когда хозяин снова увидит этот чанк.
 */
public final class PoolThroughput {

    private static final long[] THRESHOLDS = {100_000L, 1_000_000L, 10_000_000L};
    private static final String[] ADVANCEMENTS = {"throughput_100k", "throughput_1m", "throughput_10m"};

    private static final String FULL_POOL = "full_pool";
    private static final String CRITERION = "reached";

    /** «Полный круг»: продать пулам и выкупить из них эссенции по миллиону маны. */
    private static final long FULL_CIRCLE = 1_000_000L;
    /** «Мана-барон»: 16 млн в одном пуле - это полный пул пятого тира. */
    private static final int MANA_BARON = 16_000_000;
    private static final String FLOW_TAG = "manaessencebridge_flow";

    private PoolThroughput() {
    }

    /** Учитывает прошедшую ману, досылает клиентам новый счётчик и проверяет пороги. */
    public static void record(World world, BlockPos pos, InferiumCatalystCapability cap, long mana,
                              @Nullable PlayerEntity actor, boolean sold) {
        if (world.isRemote || mana <= 0) {
            return;
        }
        cap.addProcessed(mana);
        // Пулы, прокачанные до появления хозяина, получают его при первой
        // ручной операции - иначе их автоматика никому не давала бы достижений.
        if (cap.getOwner() == null && actor instanceof ServerPlayerEntity) {
            cap.setOwner(actor.getUniqueID());
        }
        ModNetwork.syncToTracking(world, pos, cap);

        ServerPlayerEntity target = actor instanceof ServerPlayerEntity
                ? (ServerPlayerEntity) actor
                : findOwner(world, cap.getOwner());
        if (target != null) {
            checkThresholds(target, cap.getProcessed());
            countFlow(target, mana, sold);
            checkBaron(target, poolMana(world, pos));
        }
    }

    /** Игрок увидел чанк с пулом: если это хозяин - проверяем, не пропустил ли он порог. */
    public static void recheck(ServerPlayerEntity player, InferiumCatalystCapability cap, int poolMana) {
        if (cap.getOwner() != null && cap.getOwner().equals(player.getUniqueID())) {
            checkThresholds(player, cap.getProcessed());
            if (cap.getTier() >= EssenceTier.SUPREMIUM.getLevel()) {
                award(player, FULL_POOL);
            }
            checkBaron(player, poolMana);
        }
    }

    /** Тир изменился руками игрока. */
    public static void onTierChanged(PlayerEntity player, InferiumCatalystCapability cap) {
        if (player instanceof ServerPlayerEntity && cap.getTier() >= EssenceTier.SUPREMIUM.getLevel()) {
            award((ServerPlayerEntity) player, FULL_POOL);
        }
    }

    private static void checkThresholds(ServerPlayerEntity player, long processed) {
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (processed >= THRESHOLDS[i]) {
                award(player, ADVANCEMENTS[i]);
            }
        }
    }

    public static void award(ServerPlayerEntity player, String name) {
        award(player, name, CRITERION);
    }

    /** Выдаёт достижение мода по одному критерию; повторный вызов ничего не делает. */
    public static void award(ServerPlayerEntity player, String name, String criterion) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement adv = server.getAdvancementManager()
                .getAdvancement(new ResourceLocation(ManaEssenceBridge.MODID, name));
        if (adv == null) {
            return; // достижение вырезано датапаком - молча пропускаем
        }
        // grantCriterion сам возвращает false, если критерий уже засчитан,
        // так что повторные вызовы безвредны.
        player.getAdvancements().grantCriterion(adv, criterion);
    }

    /**
     * Копит у игрока, сколько маны он продал пулам и сколько выкупил.
     * Лежит в persistent-данных игрока: переживает смерть и перезаход.
     */
    private static void countFlow(ServerPlayerEntity player, long mana, boolean sold) {
        net.minecraft.nbt.CompoundNBT root = player.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        net.minecraft.nbt.CompoundNBT flow = root.getCompound(FLOW_TAG);
        String key = sold ? "sold" : "bought";
        flow.putLong(key, flow.getLong(key) + mana);
        root.put(FLOW_TAG, flow);
        player.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, root);
        if (flow.getLong("sold") >= FULL_CIRCLE && flow.getLong("bought") >= FULL_CIRCLE) {
            award(player, "full_circle");
        }
    }

    private static void checkBaron(ServerPlayerEntity player, int poolMana) {
        if (poolMana >= MANA_BARON) {
            award(player, "mana_baron");
        }
    }

    private static int poolMana(World world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        return te instanceof vazkii.botania.api.mana.IManaPool ? ((vazkii.botania.api.mana.IManaPool) te).getCurrentMana() : 0;
    }

    /** Выдать достижение хозяину пула, если он сейчас в сети. */
    public static void awardOwner(World world, InferiumCatalystCapability cap, String name) {
        ServerPlayerEntity owner = findOwner(world, cap.getOwner());
        if (owner != null) {
            award(owner, name);
        }
    }

    @Nullable
    private static ServerPlayerEntity findOwner(World world, @Nullable UUID owner) {
        if (owner == null) {
            return null;
        }
        MinecraftServer server = world.getServer();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(owner);
    }
}
