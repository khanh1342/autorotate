package com.autorotate.solver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class HitPlanner {

    private HitPlanner() {
    }

    public static BlockHitResult plan(Goal goal, BlockPos target, Level level, Player player, BlockHitResult original) {
        if (goal.face() == null && goal.bias().isEmpty()) {
            return original;
        }
        Direction face = goal.face() != null ? goal.face() : original.getDirection();
        BlockPos support = target.relative(face.getOpposite());

        if (original.getDirection() == face && original.getBlockPos().equals(support)
            && biasSatisfied(goal.bias(), original.getLocation(), target)) {
            return original;
        }
        if (!isUsableSupport(level, support) || !Sight.faceVisible(player.getEyePosition(), support, face)) {
            return null;
        }

        Vec3 location = biased(faceCentre(support, face), face, goal.bias(), target);
        double reach = player.blockInteractionRange();
        if (player.getEyePosition().distanceToSqr(location) > reach * reach) {
            return null;
        }
        return new BlockHitResult(location, face, support, false);
    }

    private static Vec3 biased(Vec3 centre, Direction face, Bias bias, BlockPos target) {
        Direction.Axis axis = face.getAxis();
        double x = bias.x() != null && axis != Direction.Axis.X ? target.getX() + bias.x() : centre.x;
        double y = bias.y() != null && axis != Direction.Axis.Y ? target.getY() + bias.y() : centre.y;
        double z = bias.z() != null && axis != Direction.Axis.Z ? target.getZ() + bias.z() : centre.z;
        return new Vec3(x, y, z);
    }

    private static Vec3 faceCentre(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    private static boolean isUsableSupport(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced();
    }

    private static boolean biasSatisfied(Bias bias, Vec3 location, BlockPos target) {
        return sameHalf(bias.x(), location.x - target.getX())
            && sameHalf(bias.y(), location.y - target.getY())
            && sameHalf(bias.z(), location.z - target.getZ());
    }

    private static boolean sameHalf(Double want, double actual) {
        return want == null || (want > 0.5) == (actual > 0.5);
    }
}
