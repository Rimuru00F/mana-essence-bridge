package com.frostfirebloom.manaessencebridge;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

/**
 * Гейты прогрессии: старшие тиры нельзя открыть, пока игрок не прошёл
 * соответствующую ступень Botania.
 *
 * Сами рецепты и так недоступны раньше времени - для тира 4 нужна
 * Эльфийская Торговля (то есть открытый портал), для тира 5 нужен
 * Gaia Spirit (то есть убитый Страж). Но проверка по достижению нужна
 * отдельно: по ней прячутся записи в Ликсике Ботании и предметы в JEI,
 * и по ней же сервер отказывает в установке катализатора, если игрок
 * получил его в обход прогрессии - из чужого сундука, например.
 */
public enum ProgressGate {

    TERRASTEEL(3, "botania:main/terrasteel_pickup"),
    ALFHEIM(4, "botania:main/elf_portal_open"),
    GAIA(5, "botania:main/gaia_guardian_kill"),
    /** Шестой тир - только после Стража Гайи II (ритуал с Гайя-ядром). */
    GAIA_HARDMODE(6, "botania:challenge/gaia_guardian_hardmode");

    private final int tierLevel;
    private final ResourceLocation advancement;

    ProgressGate(int tierLevel, String advancement) {
        this.tierLevel = tierLevel;
        this.advancement = new ResourceLocation(advancement);
    }

    public int getTierLevel() {
        return tierLevel;
    }

    public ResourceLocation getAdvancement() {
        return advancement;
    }

    /** Название требования как текстовый компонент - для сообщения игроку. */
    public Component getRequirement() {
        return new TranslatableComponent(
                "gate." + ManaEssenceBridge.MODID + "." + name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Гейт для тира, или null если тир не заперт. */
    public static ProgressGate forTier(int tierLevel) {
        for (ProgressGate gate : values()) {
            if (gate.tierLevel == tierLevel) {
                return gate;
            }
        }
        return null;
    }

    /**
     * Открыт ли гейт для игрока.
     *
     * Серверная проверка зовёт это без аргумента и при неизвестном результате
     * ПРОПУСКАЕТ: если достижения нет (скажем, другая версия Botania), игрок
     * не должен оказаться заблокирован навсегда.
     *
     * А маска для JEI зовёт с unknownMeansUnlocked=false и при тех же
     * обстоятельствах ЗАПИРАЕТ: показать лишнее хуже, чем спрятать лишнее,
     * и это всего лишь косметика.
     */
    public boolean isUnlockedFor(ServerPlayer player) {
        return isUnlockedFor(player, true);
    }

    public boolean isUnlockedFor(ServerPlayer player, boolean unknownMeansUnlocked) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return unknownMeansUnlocked;
        }
        Advancement adv = server.getAdvancements().getAdvancement(advancement);
        if (adv == null) {
            return unknownMeansUnlocked;
        }
        return player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    /** Битовая маска открытых гейтов - её и шлём клиенту. */
    public static int maskFor(ServerPlayer player) {
        int mask = 0;
        for (ProgressGate gate : values()) {
            if (gate.isUnlockedFor(player, false)) {
                mask |= 1 << gate.ordinal();
            }
        }
        return mask;
    }

    /** Открыт ли тир при такой маске. Тиры без гейта открыты всегда. */
    public static boolean isTierUnlocked(int mask, int tierLevel) {
        ProgressGate gate = forTier(tierLevel);
        return gate == null || (mask & (1 << gate.ordinal())) != 0;
    }

    /** Затрагивает ли это достижение какой-либо из гейтов. */
    public static boolean isGateAdvancement(ResourceLocation id) {
        for (ProgressGate gate : values()) {
            if (gate.advancement.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
