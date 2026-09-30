package com.autorotate.solver;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public record Aim(Float yaw, Float pitch) {

    public static final Aim NONE = new Aim(null, null);

    private static final float MARGIN = 1.5F;
    private static final float HORIZONTAL_LIMIT = (float) Math.toDegrees(Math.atan(1.0 / Math.sqrt(2.0))) - MARGIN;
    private static final float VERTICAL_THRESHOLD = 45.0F + 8.0F;
    private static final float EPSILON = 1.0E-4F;

    public boolean isEmpty() {
        return yaw == null && pitch == null;
    }

    public boolean isCurrent(float currentYaw, float currentPitch) {
        boolean yawHeld = yaw == null || Math.abs(Mth.wrapDegrees(yaw - currentYaw)) < EPSILON;
        boolean pitchHeld = pitch == null || Math.abs(pitch - currentPitch) < EPSILON;
        return yawHeld && pitchHeld;
    }

    public static float horizontalYaw(Direction want, float currentYaw) {
        return clampAround(want.toYRot(), currentYaw, 45.0F);
    }

    public static float segmentYaw(int segment, float currentYaw, float offset) {
        return clampAround(Mth.wrapDegrees(segment * 22.5F - offset), currentYaw, 11.25F);
    }

    public static Float keepHorizontal(float currentPitch) {
        if (Math.abs(currentPitch) <= HORIZONTAL_LIMIT) {
            return null;
        }
        return currentPitch > 0 ? HORIZONTAL_LIMIT : -HORIZONTAL_LIMIT;
    }

    public static float lookVertical(Direction vertical, float currentPitch) {
        float target = vertical == Direction.UP ? -VERTICAL_THRESHOLD : VERTICAL_THRESHOLD;
        boolean alreadyPast = vertical == Direction.UP ? currentPitch <= target : currentPitch >= target;
        return alreadyPast ? currentPitch : target;
    }

    private static float clampAround(float centre, float current, float halfWidth) {
        float delta = Mth.wrapDegrees(current - centre);
        float clamped = Mth.clamp(delta, -halfWidth + MARGIN, halfWidth - MARGIN);
        return Mth.wrapDegrees(centre + clamped);
    }
}
