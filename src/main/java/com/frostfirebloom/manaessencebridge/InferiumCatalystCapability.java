package com.frostfirebloom.manaessencebridge;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Состояние прокачки конкретного Mana Pool: до какого тира эссенций
 * он умеет конвертировать. 0 - обычный непрокачанный пул.
 *
 * Прикрепляется к TileEntity через Forge Capability систему -
 * никаких Mixin, никаких правок чужого байткода.
 */
public class InferiumCatalystCapability {

    private int tier = 0;

    /** Сколько маны прошло через пул в обе стороны за всю его жизнь. */
    private long processed = 0L;

    /**
     * Кто поставил первый катализатор. Нужен, чтобы достижения за оборот
     * доставались хозяину пула и тогда, когда конвертирует автоматика,
     * а не игрок с эссенцией в руке.
     */
    private UUID owner;

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public boolean isUpgraded() {
        return tier > 0;
    }

    /** Умеет ли пул работать с эссенцией этого тира. */
    public boolean supports(EssenceTier essence) {
        return essence != null && essence.getLevel() <= tier;
    }

    public long getProcessed() {
        return processed;
    }

    public void setProcessed(long processed) {
        this.processed = Math.max(0L, processed);
    }

    public void addProcessed(long mana) {
        if (mana > 0) {
            processed += mana;
        }
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    /**
     * Забирает ли пул эссенцию из соседних контейнеров сам. Выключено по
     * умолчанию и включается Сифоном эссенции: пул, который до обновления
     * просто стоял рядом с сундуком эссенции, не должен молча начать его
     * опустошать.
     */
    private boolean pullEnabled = false;

    public boolean isPullEnabled() {
        return pullEnabled;
    }

    public void setPullEnabled(boolean pullEnabled) {
        this.pullEnabled = pullEnabled;
    }

    /**
     * Родная ёмкость пула до прокачки, 0 - ещё не известна. Нужна, чтобы
     * множитель считался от неё, а не накручивался поверх уже увеличенной.
     */
    private int baseCapacity = 0;

    public int getBaseCapacity() {
        return baseCapacity;
    }

    public void setBaseCapacity(int baseCapacity) {
        this.baseCapacity = Math.max(0, baseCapacity);
    }

    /**
     * Сеть пулов: с какими пулами того же измерения этот связан (Зеркалом
     * эссенции). Связь всегда двусторонняя; мана между ними выравнивается
     * раз в секунду (PoolNetwork).
     */
    private final java.util.List<BlockPos> links = new java.util.ArrayList<>();

    public java.util.List<BlockPos> getLinks() {
        return java.util.Collections.unmodifiableList(links);
    }

    public boolean hasLink(BlockPos pos) {
        return links.contains(pos);
    }

    public void addLink(BlockPos pos) {
        if (!links.contains(pos)) {
            links.add(pos.toImmutable());
        }
    }

    public void removeLink(BlockPos pos) {
        links.remove(pos);
    }

    public void setLinks(java.util.Collection<BlockPos> positions) {
        links.clear();
        for (BlockPos p : positions) {
            addLink(p);
        }
    }
}
