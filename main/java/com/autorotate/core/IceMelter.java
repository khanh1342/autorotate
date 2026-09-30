package com.autorotate.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public final class IceMelter {

    private static final int PLACE_TIMEOUT_TICKS = 40;
    private static final int CONFIRM_TICKS = 2;
    private static final Predicate<ItemStack> MELTS = stack -> !EnchantmentHelper.hasTag(stack, EnchantmentTags.PREVENTS_ICE_MELTING);

    private static BlockPos target;
    private static int ticks;
    private static int confirmed;

    private IceMelter() {
    }

    public static boolean isActive() {
        return target != null;
    }

    public static String problem(LocalPlayer player, Level level, BlockPos source) {
        if (player.hasInfiniteMaterials()) {
            return "iceCreative";
        }
        if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, source)) {
            return "iceEvaporates";
        }
        BlockState below = level.getBlockState(source.below());
        if (!below.blocksMotion() && !below.liquid()) {
            return "iceNoFloor";
        }
        if (BlockBreaker.bestTool(player, Blocks.ICE.defaultBlockState(), MELTS) < 0) {
            return "iceSilkTouch";
        }
        return null;
    }

    public static void begin(BlockPos source) {
        target = source.immutable();
        ticks = 0;
        confirmed = 0;
        Debug.log("Placing ice at {} to melt it into water", target);
    }

    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (target == null) {
            return;
        }
        ticks++;
        if (!minecraft.level.getBlockState(target).is(Blocks.ICE)) {
            confirmed = 0;
            if (ticks > PLACE_TIMEOUT_TICKS) {
                BlockPos failed = target;
                target = null;
                Notify.refuse(player, "iceFailed", Notify.position(failed));
            }
            return;
        }
        if (++confirmed < CONFIRM_TICKS) {
            return;
        }
        BlockPos ice = target;
        target = null;
        if (!BlockBreaker.start(minecraft.level, player, ice, MELTS)) {
            Notify.refuse(player, "iceFailed", Notify.position(ice));
        }
    }

    public static void cancel() {
        target = null;
    }
}
