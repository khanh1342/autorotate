package com.autorotate.core;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DiodeTuner {

    private static final long TIMEOUT_MS = 2000;
    private static final int NOT_PLACED_YET = -1;

    private static final List<Job> JOBS = new ArrayList<>();

    private static final class Job {
        private final BlockPos pos;
        private final InteractionHand hand;
        private final long createdAt = System.currentTimeMillis();
        private int clicksLeft = NOT_PLACED_YET;

        private Job(BlockPos pos, InteractionHand hand) {
            this.pos = pos;
            this.hand = hand;
        }
    }

    private DiodeTuner() {
    }

    public static void track(BlockPos pos, InteractionHand hand) {
        JOBS.add(new Job(pos.immutable(), hand));
    }

    public static void clear() {
        JOBS.clear();
    }

    public static void tick(Level level, LocalPlayer player) {
        Level schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) {
            clear();
            return;
        }
        boolean clicked = false;
        Iterator<Job> iterator = JOBS.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (System.currentTimeMillis() - job.createdAt > TIMEOUT_MS || !inReach(player, job.pos)) {
                iterator.remove();
                continue;
            }
            if (job.clicksLeft == NOT_PLACED_YET) {
                job.clicksLeft = clicksNeeded(level.getBlockState(job.pos), schematic.getBlockState(job.pos));
                if (job.clicksLeft == NOT_PLACED_YET) {
                    continue;
                }
            }
            if (job.clicksLeft == 0) {
                iterator.remove();
                continue;
            }
            if (clicked || Executor.isBusy() || Cooldown.sentThisTick()) {
                continue;
            }
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(job.pos), Direction.UP, job.pos, false);
            Actions.useOn(player, job.hand, hit, false);
            job.clicksLeft--;
            clicked = true;
        }
    }

    private static int clicksNeeded(BlockState placed, BlockState wanted) {
        if (placed.isAir()) {
            return NOT_PLACED_YET;
        }
        if (placed.getBlock() instanceof RepeaterBlock && wanted.getBlock() instanceof RepeaterBlock) {
            int delay = BlockStateProperties.DELAY.getPossibleValues().size();
            return Math.floorMod(wanted.getValue(BlockStateProperties.DELAY) - placed.getValue(BlockStateProperties.DELAY), delay);
        }
        if (placed.getBlock() instanceof ComparatorBlock && wanted.getBlock() instanceof ComparatorBlock) {
            return placed.getValue(BlockStateProperties.MODE_COMPARATOR) == wanted.getValue(BlockStateProperties.MODE_COMPARATOR) ? 0 : 1;
        }
        return 0;
    }

    private static boolean inReach(LocalPlayer player, BlockPos pos) {
        double reach = player.blockInteractionRange();
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach;
    }
}
