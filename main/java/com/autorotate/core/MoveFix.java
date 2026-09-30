package com.autorotate.core;

import com.autorotate.config.Config;
import com.autorotate.mixin.ClientInputAccessor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public final class MoveFix {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private static int depth;
    private static float realYaw;
    private static float realPitch;

    private MoveFix() {
    }

    public static void remapInput(LocalPlayer player) {
        ClientInput input = player.input;
        Vec2 move = input.getMoveVector();
        if (!active() || move.lengthSquared() < 1.0E-7F) {
            return;
        }
        float spoofYaw = spoofYaw();
        float[] wanted = toWorld(move.x, move.y, player.getYRot());
        int bestForward = 0;
        int bestStrafe = 0;
        float bestAlignment = -Float.MAX_VALUE;
        for (int forward = -1; forward <= 1; forward++) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                if (forward == 0 && strafe == 0) {
                    continue;
                }
                Vec2 candidate = new Vec2(strafe, forward).normalized();
                float[] world = toWorld(candidate.x, candidate.y, spoofYaw);
                float alignment = world[0] * wanted[0] + world[1] * wanted[1];
                if (alignment > bestAlignment) {
                    bestAlignment = alignment;
                    bestForward = forward;
                    bestStrafe = strafe;
                }
            }
        }
        Input keys = input.keyPresses;
        input.keyPresses = new Input(bestForward > 0, bestForward < 0, bestStrafe > 0, bestStrafe < 0,
            keys.jump(), keys.shift(), keys.sprint());
        ((ClientInputAccessor) input).autorotate$setMoveVector(new Vec2(bestStrafe, bestForward).normalized());
    }

    public static void enter(Entity entity) {
        if (!(entity instanceof LocalPlayer player)) {
            return;
        }
        if (!active() && depth == 0) {
            return;
        }
        if (depth++ == 0) {
            realYaw = player.getYRot();
            realPitch = player.getXRot();
            player.setYRot(spoofYaw());
            player.setXRot(spoofPitch());
        }
    }

    public static void exit(Entity entity) {
        if (!(entity instanceof LocalPlayer player) || depth == 0) {
            return;
        }
        if (--depth == 0) {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
        }
    }

    private static boolean active() {
        return Config.get().moveFix && (substitutingSession() != null || BlockBreaker.isSubstituting());
    }

    private static RotationSession substitutingSession() {
        RotationSession session = Executor.session();
        return session != null && session.isSubstituting() ? session : null;
    }

    private static float spoofYaw() {
        RotationSession session = substitutingSession();
        return session != null ? session.yaw() : BlockBreaker.yaw();
    }

    private static float spoofPitch() {
        RotationSession session = substitutingSession();
        return session != null ? session.pitch() : BlockBreaker.pitch();
    }

    private static float[] toWorld(float strafe, float forward, float yaw) {
        float sin = Mth.sin(yaw * DEG_TO_RAD);
        float cos = Mth.cos(yaw * DEG_TO_RAD);
        float x = strafe * cos - forward * sin;
        float z = forward * cos + strafe * sin;
        float length = Mth.sqrt(x * x + z * z);
        return length < 1.0E-6F ? new float[]{0.0F, 0.0F} : new float[]{x / length, z / length};
    }
}
