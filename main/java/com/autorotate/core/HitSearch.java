package com.autorotate.core;

import com.autorotate.config.Config;
import com.autorotate.solver.Aim;
import com.autorotate.solver.Bias;
import com.autorotate.solver.Goal;
import com.autorotate.solver.HitPlanner;
import com.autorotate.solver.Sight;
import com.autorotate.solver.Simulator;
import com.autorotate.solver.SneakRule;
import com.autorotate.solver.StateSolver;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class HitSearch {

    private static final double LOWER_HALF = 0.25;
    private static final double UPPER_HALF = 0.75;

    public record Candidate(BlockHitResult hit, Aim aim, BlockState predicted, boolean shift,
                            boolean viaInteractive, boolean inSight, boolean unpaired) {

        private int rank() {
            return (inSight ? 0 : 2) + (viaInteractive ? 1 : 0);
        }
    }

    private record Request(LocalPlayer player, InteractionHand hand, ItemStack held, Level level,
                           BlockPos target, BlockState want, SneakRule sneak, boolean unpaired) {
    }

    private HitSearch() {
    }

    public static Candidate best(LocalPlayer player, InteractionHand hand, ItemStack held, Level level,
                                 BlockHitResult original, BlockHitResult planned, BlockPos target,
                                 BlockState want, Goal goal) {
        Candidate exact = search(new Request(player, hand, held, level, target, want, goal.sneak(), false),
            original, planned, goal);
        if (exact != null || !awaitsChestPartner(level, target, want)) {
            return exact;
        }
        return search(new Request(player, hand, held, level, target, want, goal.sneak(), true), original, planned, goal);
    }

    public static BlockHitResult supportHit(Level level, LocalPlayer player, BlockPos target) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        BlockHitResult fallback = null;
        for (Direction face : Direction.values()) {
            BlockPos support = target.relative(face.getOpposite());
            BlockState state = level.getBlockState(support);
            if (state.isAir() || state.canBeReplaced() || !Sight.faceVisible(eye, support, face)) {
                continue;
            }
            Vec3 location = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            BlockHitResult hit = new BlockHitResult(location, face, support, false);
            if (eye.distanceToSqr(location) > reach * reach) {
                continue;
            }
            Aim look = Sight.lookAt(eye, location);
            if (look != null && Sight.sees(level, player, look.yaw(), look.pitch(), hit)) {
                return hit;
            }
            if (fallback == null) {
                fallback = hit;
            }
        }
        return fallback;
    }

    public static BlockPos chestPartner(BlockPos target, BlockState want) {
        if (!want.hasProperty(BlockStateProperties.CHEST_TYPE) || want.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
            return null;
        }
        return target.relative(ChestBlock.getConnectedDirection(want));
    }

    public static boolean awaitsChestPartner(Level level, BlockPos target, BlockState want) {
        BlockPos partner = chestPartner(target, want);
        return partner != null && !level.getBlockState(partner).is(want.getBlock());
    }

    private static Candidate search(Request request, BlockHitResult original, BlockHitResult planned, Goal goal) {
        Candidate best = test(request, planned, goal.aim());
        if (best != null && best.rank() == 0) {
            return best;
        }
        for (BlockHitResult hit : hits(request, original)) {
            for (Aim aim : aims(request, goal, hit)) {
                Candidate candidate = test(request, hit, aim);
                if (candidate == null) {
                    continue;
                }
                if (candidate.rank() == 0) {
                    return candidate;
                }
                if (best == null || candidate.rank() < best.rank()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    public static BlockHitResult quiet(LocalPlayer player, InteractionHand hand, ItemStack held, Level level,
                                       BlockHitResult hit, BlockPos target, BlockState want) {
        BlockState asAimed = Simulator.predict(player, hand, held, hit, target, null, null, true);
        BlockState reference = want != null ? want : asAimed;
        if (reference == null) {
            return null;
        }
        boolean mustMatch = Simulator.matches(asAimed, reference);
        boolean shift = player.isShiftKeyDown();
        for (Direction face : Direction.values()) {
            BlockHitResult candidate = HitPlanner.plan(Goal.face(face), target, level, player, hit);
            if (candidate == null || candidate == hit
                || Interactions.isInteractive(level.getBlockState(candidate.getBlockPos()))) {
                continue;
            }
            BlockState predicted = Simulator.predict(player, hand, held, candidate, target, null, null, shift);
            if (predicted == null || predicted.getBlock() != reference.getBlock()) {
                continue;
            }
            if (!mustMatch || Simulator.matches(predicted, reference)) {
                return candidate;
            }
        }
        return null;
    }

    public static Aim lookingAt(LocalPlayer player, BlockHitResult hit) {
        Aim look = Sight.lookAt(player.getEyePosition(), hit.getLocation());
        return look != null ? look : Aim.NONE;
    }

    private static Candidate test(Request request, BlockHitResult hit, Aim aim) {
        LocalPlayer player = request.player();
        boolean interactive = Interactions.isInteractive(request.level().getBlockState(hit.getBlockPos()));
        for (boolean shift : request.sneak().options(interactive, player.isShiftKeyDown())) {
            BlockState predicted = Simulator.predict(player, request.hand(), request.held(), hit,
                request.target(), aim.yaw(), aim.pitch(), shift);
            if (Simulator.matches(predicted, request.want(), request.unpaired())) {
                float yaw = aim.yaw() != null ? aim.yaw() : player.getYRot();
                float pitch = aim.pitch() != null ? aim.pitch() : player.getXRot();
                boolean inSight = Sight.sees(request.level(), player, yaw, pitch, hit);
                return new Candidate(hit, aim, predicted, shift, interactive, inSight, request.unpaired());
            }
        }
        return null;
    }

    private static List<BlockHitResult> hits(Request request, BlockHitResult original) {
        List<BlockHitResult> hits = new ArrayList<>(11);
        hits.add(original);
        if (!Config.get().controlClickedFace) {
            return hits;
        }
        for (Direction face : Direction.values()) {
            if (face.getAxis().isHorizontal()) {
                addPlan(hits, request, original, Goal.of(Aim.NONE, face, Bias.ofY(LOWER_HALF)));
                addPlan(hits, request, original, Goal.of(Aim.NONE, face, Bias.ofY(UPPER_HALF)));
            } else {
                addPlan(hits, request, original, Goal.face(face));
            }
        }
        return hits;
    }

    private static void addPlan(List<BlockHitResult> hits, Request request, BlockHitResult original, Goal goal) {
        BlockHitResult hit = HitPlanner.plan(goal, request.target(), request.level(), request.player(), original);
        if (hit != null && !hits.contains(hit)) {
            hits.add(hit);
        }
    }

    private static List<Aim> aims(Request request, Goal goal, BlockHitResult hit) {
        LocalPlayer player = request.player();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        List<Aim> aims = new ArrayList<>(8);
        aims.add(aimAt(request, hit));
        aims.add(goal.aim());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            aims.add(new Aim(Aim.horizontalYaw(direction, yaw), Aim.keepHorizontal(pitch)));
        }
        aims.add(new Aim(null, Aim.lookVertical(Direction.UP, pitch)));
        aims.add(new Aim(null, Aim.lookVertical(Direction.DOWN, pitch)));
        return aims;
    }

    private static Aim aimAt(Request request, BlockHitResult hit) {
        Aim look = lookingAt(request.player(), hit);
        if (look.isEmpty()) {
            return Aim.NONE;
        }
        Goal atHit = StateSolver.solve(request.want(), look.yaw(), look.pitch());
        Aim solved = atHit != null ? atHit.aim() : Aim.NONE;
        return new Aim(
            solved.yaw() != null ? solved.yaw() : look.yaw(),
            solved.pitch() != null ? solved.pitch() : look.pitch());
    }
}
