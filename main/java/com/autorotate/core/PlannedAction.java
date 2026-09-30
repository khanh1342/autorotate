package com.autorotate.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;

public record PlannedAction(InteractionHand hand, BlockHitResult hit, BlockHitResult origin, BlockPos target,
                            boolean shift, Item item, boolean readsHeadYaw) {

    public static PlannedAction block(InteractionHand hand, BlockHitResult hit, BlockHitResult origin, BlockPos target,
                                      boolean shift, Item item) {
        return new PlannedAction(hand, hit, origin, target, shift, item, false);
    }

    public static PlannedAction fluid(InteractionHand hand, BlockHitResult origin, BlockPos target, Item bucket) {
        return new PlannedAction(hand, null, origin, target, false, bucket, false);
    }

    public PlannedAction readingHeadYaw(boolean reads) {
        return new PlannedAction(hand, hit, origin, target, shift, item, reads);
    }

    public boolean isFluid() {
        return hit == null;
    }
}
