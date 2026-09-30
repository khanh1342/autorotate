package com.autorotate.core;

import com.autorotate.solver.Aim;
import com.autorotate.solver.Sight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class BlockBreaker {

    private enum Phase {
        AIMING,
        BREAKING
    }

    private static final int AIM_TIMEOUT_TICKS = 10;
    private static final int BREAK_TIMEOUT_TICKS = 200;

    private static Phase phase;
    private static BlockPos target;
    private static Direction face;
    private static int ticks;
    private static float yaw;
    private static float pitch;
    private static boolean delivered;
    private static boolean stopping;
    private static boolean substituted;
    private static float savedYaw;
    private static float savedPitch;
    private static float savedHeadYaw;

    private BlockBreaker() {
    }

    public static boolean isActive() {
        return phase != null;
    }

    public static boolean isBreaking() {
        return phase == Phase.BREAKING;
    }

    public static boolean isStopping() {
        return stopping;
    }

    public static boolean isSubstituting() {
        return phase != null;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static int bestTool(LocalPlayer player, BlockState state, Predicate<ItemStack> allowed) {
        Inventory inventory = player.getInventory();
        int best = -1;
        float bestSpeed = -1.0F;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!allowed.test(stack)) {
                continue;
            }
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    public static boolean start(Level level, LocalPlayer player, BlockPos pos, Predicate<ItemStack> allowed) {
        int tool = bestTool(player, level.getBlockState(pos), allowed);
        if (tool < 0) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        BlockHitResult chosen = null;
        Aim chosenAim = null;
        for (Direction direction : Direction.values()) {
            if (!Sight.faceVisible(eye, pos, direction)) {
                continue;
            }
            Vec3 point = Vec3.atCenterOf(pos).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5,
                direction.getStepZ() * 0.5);
            Aim look = Sight.lookAt(eye, point);
            if (look == null) {
                continue;
            }
            BlockHitResult hit = new BlockHitResult(point, direction, pos, false);
            boolean seen = Sight.sees(level, player, look.yaw(), look.pitch(), hit);
            if (chosen == null || seen) {
                chosen = hit;
                chosenAim = look;
                if (seen) {
                    break;
                }
            }
        }
        if (chosen == null) {
            chosenAim = Sight.lookAt(eye, Vec3.atCenterOf(pos));
            chosen = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        }
        if (chosenAim == null) {
            return false;
        }
        player.getInventory().setSelectedSlot(tool);
        Hotbar.markUsed(tool);
        target = pos.immutable();
        face = chosen.getDirection();
        yaw = player.getYRot() + Mth.wrapDegrees(chosenAim.yaw() - player.getYRot());
        pitch = chosenAim.pitch();
        delivered = false;
        ticks = 0;
        phase = Phase.AIMING;
        Debug.log("Breaking {} at {}", level.getBlockState(pos).getBlock(), target);
        return true;
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (phase == null) {
            return;
        }
        ticks++;
        if (minecraft.level.getBlockState(target).canBeReplaced()) {
            Debug.log("Broke the block at {}", target);
            phase = null;
            return;
        }
        switch (phase) {
            case AIMING -> {
                if (delivered) {
                    phase = Phase.BREAKING;
                    ticks = 0;
                } else if (ticks > AIM_TIMEOUT_TICKS) {
                    fail(minecraft, player);
                }
            }
            case BREAKING -> {
                if (ticks > BREAK_TIMEOUT_TICKS) {
                    fail(minecraft, player);
                } else {
                    breakStep(minecraft, player);
                }
            }
        }
    }

    public static void beforeSend(LocalPlayer player) {
        if (!isSubstituting()) {
            return;
        }
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();
        savedHeadYaw = player.getYHeadRot();
        apply(player);
        substituted = true;
    }

    public static void afterSend(LocalPlayer player, float sentYaw, float sentPitch) {
        if (!substituted) {
            return;
        }
        restore(player);
        substituted = false;
        if (sentYaw == yaw && sentPitch == pitch) {
            delivered = true;
        }
    }

    public static void cancel(Minecraft minecraft) {
        if (phase == Phase.BREAKING && minecraft.gameMode != null) {
            stopping = true;
            try {
                minecraft.gameMode.stopDestroyBlock();
            } finally {
                stopping = false;
            }
        }
        phase = null;
    }

    private static void breakStep(Minecraft minecraft, LocalPlayer player) {
        float realYaw = player.getYRot();
        float realPitch = player.getXRot();
        float realHeadYaw = player.getYHeadRot();
        apply(player);
        try {
            if (minecraft.gameMode.continueDestroyBlock(target, face)) {
                minecraft.level.addBreakingBlockEffect(target, face);
                player.swing(InteractionHand.MAIN_HAND);
            }
        } finally {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
            player.setYHeadRot(realHeadYaw);
        }
    }

    private static void fail(Minecraft minecraft, LocalPlayer player) {
        BlockPos failed = target;
        cancel(minecraft);
        Notify.refuse(player, "breakFailed", Notify.position(failed));
    }

    private static void apply(LocalPlayer player) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    private static void restore(LocalPlayer player) {
        player.setYRot(savedYaw);
        player.setXRot(savedPitch);
        player.setYHeadRot(savedHeadYaw);
    }
}
