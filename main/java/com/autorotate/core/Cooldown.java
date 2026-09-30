package com.autorotate.core;

import com.autorotate.config.Config;

public final class Cooldown {

    private static long lastPlacedAt;
    private static long lastSentTick = Long.MIN_VALUE;

    private Cooldown() {
    }

    public static void mark() {
        lastPlacedAt = System.currentTimeMillis();
    }

    public static void markSent() {
        lastSentTick = Executor.now();
    }

    public static boolean sentThisTick() {
        return Config.get().onePlacePerTick && lastSentTick == Executor.now();
    }

    public static boolean active() {
        if (sentThisTick()) {
            return true;
        }
        int cooldown = Config.get().placeCooldownMs;
        return cooldown > 0 && System.currentTimeMillis() - lastPlacedAt < cooldown;
    }
}
