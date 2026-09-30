package com.autorotate.core;

import com.autorotate.config.Config;
import com.autorotate.solver.Aim;
import com.autorotate.solver.Sight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class ServerRotation {

    private static boolean known;
    private static float yaw;
    private static float pitch;
    private static int ticksSinceChange;

    private ServerRotation() {
    }

    public static void onSent(float sentYaw, float sentPitch) {
        if (!known || sentYaw != yaw || sentPitch != pitch) {
            known = true;
            yaw = sentYaw;
            pitch = sentPitch;
            ticksSinceChange = 0;
        }
    }

    public static void tick() {
        if (ticksSinceChange < Integer.MAX_VALUE) {
            ticksSinceChange++;
        }
    }

    public static void reset() {
        known = false;
    }

    public static boolean isKnown() {
        return known;
    }

    public static boolean isSettled(boolean readsHeadYaw) {
        return known && (!readsHeadYaw || ticksSinceChange > Config.get().headSettleTicks);
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static boolean sees(Level level, LocalPlayer player, BlockHitResult hit) {
        return known && Sight.sees(level, player, yaw, pitch, hit);
    }

    public static boolean holds(Aim aim) {
        return known && aim.isCurrent(yaw, pitch);
    }

    public static void runAt(LocalPlayer player, Runnable action) {
        if (!known) {
            action.run();
            return;
        }
        float realYaw = player.getYRot();
        float realPitch = player.getXRot();
        float realHeadYaw = player.getYHeadRot();
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        try {
            action.run();
        } finally {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
            player.setYHeadRot(realHeadYaw);
        }
    }
}
