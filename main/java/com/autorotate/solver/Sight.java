package com.autorotate.solver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Sight {

    private Sight() {
    }

    public static Aim lookAt(Vec3 eye, Vec3 point) {
        Vec3 delta = point.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-7 && Math.abs(delta.y) < 1.0E-7) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return new Aim(yaw, pitch);
    }

    public static BlockHitResult ray(Level level, Player player, float yaw, float pitch) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.calculateViewVector(pitch, yaw).scale(player.blockInteractionRange()));
        return level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    public static boolean sees(Level level, Player player, float yaw, float pitch, BlockHitResult hit) {
        BlockHitResult seen = ray(level, player, yaw, pitch);
        return seen.getType() == HitResult.Type.BLOCK
            && seen.getBlockPos().equals(hit.getBlockPos())
            && seen.getDirection() == hit.getDirection();
    }

    public static boolean faceVisible(Vec3 eye, BlockPos pos, Direction face) {
        return switch (face) {
            case UP -> eye.y > pos.getY() + 1;
            case DOWN -> eye.y < pos.getY();
            case EAST -> eye.x > pos.getX() + 1;
            case WEST -> eye.x < pos.getX();
            case SOUTH -> eye.z > pos.getZ() + 1;
            case NORTH -> eye.z < pos.getZ();
        };
    }
}
