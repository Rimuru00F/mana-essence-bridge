package com.frostfirebloom.manaessencebridge;

/**
 * Состояние прокачки конкретного Mana Pool: до какого тира эссенций
 * он умеет конвертировать. 0 - обычный непрокачанный пул.
 *
 * Прикрепляется к BlockEntity через Forge Capability систему -
 * никаких Mixin, никаких правок чужого байткода.
 */
public class InferiumCatalystCapability {

    private int tier = 0;

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
}
