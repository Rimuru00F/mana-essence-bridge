package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.ProgressGate;

/**
 * Клиентская копия маски открытых гейтов. Нужна только для того,
 * чтобы JEI прятал катализаторы тиров, до которых игрок ещё не дошёл.
 */
public final class ClientGates {

    private static int mask = 0;
    private static Runnable listener;

    private ClientGates() {
    }

    public static void set(int newMask) {
        boolean changed = mask != newMask;
        mask = newMask;
        if (changed && listener != null) {
            listener.run();
        }
    }

    public static boolean isTierUnlocked(int tierLevel) {
        return ProgressGate.isTierUnlocked(mask, tierLevel);
    }

    /** JEI-плагин подписывается сюда, чтобы перепрятывать предметы при смене маски. */
    public static void setListener(Runnable runnable) {
        listener = runnable;
    }
}
