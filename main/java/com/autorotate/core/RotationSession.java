package com.autorotate.core;

import com.autorotate.config.Config;
import com.autorotate.solver.Aim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class RotationSession {

    public enum Outcome {
        WAITING,
        READY,
        FAILED,
        FINISHED
    }

    private enum Phase {
        AIMING,
        DELIVERING,
        SETTLING,
        RETURNING,
        FINISHED
    }

    private static final float TURN_SCALE = 0.15F;

    private final PlannedAction action;
    private final boolean realAim;
    private final double maxStep;
    private final int settleTicks;
    private final int maxAimTicks;
    private final int maxDeliveryTicks;
    private final float targetYaw;
    private final float targetPitch;

    private Phase phase = Phase.AIMING;
    private int ticksInPhase;
    private float yaw;
    private float pitch;
    private float ownYaw;
    private float ownPitch;
    private float savedYaw;
    private float savedPitch;
    private float savedHeadYaw;
    private boolean substituted;

    public RotationSession(LocalPlayer player, PlannedAction action, Aim aim) {
        Config config = Config.get();
        this.action = action;
        this.realAim = config.realAim;
        this.maxStep = config.maxAimStepDegrees;
        this.settleTicks = realAim ? config.realAimSettleTicks : action.readsHeadYaw() ? config.headSettleTicks : 0;
        this.maxAimTicks = config.maxAimTicks;
        this.maxDeliveryTicks = config.maxSpoofWaitTicks;
        this.ownYaw = player.getYRot();
        this.ownPitch = player.getXRot();
        this.yaw = ownYaw;
        this.pitch = ownPitch;
        this.targetYaw = aim.yaw() == null ? ownYaw : ownYaw + Mth.wrapDegrees(aim.yaw() - ownYaw);
        this.targetPitch = aim.pitch() == null ? ownPitch : Mth.clamp(aim.pitch(), -90.0F, 90.0F);
        stepToward(player, targetYaw, targetPitch);
    }

    public PlannedAction action() {
        return action;
    }

    public boolean isSubstituting() {
        return !realAim && (phase == Phase.AIMING || phase == Phase.DELIVERING || phase == Phase.SETTLING);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    public Outcome tick(LocalPlayer player) {
        ticksInPhase++;
        return switch (phase) {
            case AIMING -> {
                if (ticksInPhase > maxAimTicks) {
                    yield Outcome.FAILED;
                }
                stepToward(player, targetYaw, targetPitch);
                yield Outcome.WAITING;
            }
            case DELIVERING -> ticksInPhase > maxDeliveryTicks ? Outcome.FAILED : Outcome.WAITING;
            case SETTLING -> ticksInPhase > settleTicks ? Outcome.READY : Outcome.WAITING;
            case RETURNING -> {
                stepToward(player, ownYaw, ownPitch);
                yield isFinished() ? Outcome.FINISHED : Outcome.WAITING;
            }
            case FINISHED -> Outcome.FINISHED;
        };
    }

    public void beforeSend(LocalPlayer player) {
        if (!isSubstituting()) {
            return;
        }
        save(player);
        apply(player, yaw, pitch);
        substituted = true;
    }

    public void afterSend(LocalPlayer player, float sentYaw, float sentPitch) {
        if (substituted) {
            restore(player);
            substituted = false;
        }
        if (phase == Phase.DELIVERING && sentYaw == yaw && sentPitch == pitch) {
            enter(Phase.SETTLING);
        }
    }

    public void fire(LocalPlayer player, Runnable placement) {
        if (realAim) {
            placement.run();
            enter(Phase.RETURNING);
            stepToward(player, ownYaw, ownPitch);
            return;
        }
        save(player);
        apply(player, yaw, pitch);
        try {
            placement.run();
        } finally {
            restore(player);
            enter(Phase.FINISHED);
        }
    }

    public void abort(LocalPlayer player) {
        if (realAim && phase != Phase.FINISHED) {
            player.setYRot(ownYaw);
            player.setXRot(ownPitch);
        }
        enter(Phase.FINISHED);
    }

    public boolean absorbTurn(double yawDelta, double pitchDelta) {
        if (!realAim || phase == Phase.FINISHED) {
            return false;
        }
        ownYaw += (float) yawDelta * TURN_SCALE;
        ownPitch = Mth.clamp(ownPitch + (float) pitchDelta * TURN_SCALE, -90.0F, 90.0F);
        return true;
    }

    private void stepToward(LocalPlayer player, float toYaw, float toPitch) {
        yaw = step(yaw, toYaw);
        pitch = step(pitch, toPitch);
        if (realAim) {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
        if (yaw == toYaw && pitch == toPitch) {
            enter(phase == Phase.RETURNING ? Phase.FINISHED : Phase.DELIVERING);
        }
    }

    private float step(float from, float to) {
        float delta = to - from;
        if (maxStep <= 0.0 || Math.abs(delta) <= maxStep) {
            return to;
        }
        return from + (float) Math.copySign(maxStep, delta);
    }

    private void enter(Phase next) {
        phase = next;
        ticksInPhase = 0;
    }

    private void save(LocalPlayer player) {
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();
        savedHeadYaw = player.getYHeadRot();
    }

    private void restore(LocalPlayer player) {
        player.setYRot(savedYaw);
        player.setXRot(savedPitch);
        player.setYHeadRot(savedHeadYaw);
    }

    private static void apply(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }
}
