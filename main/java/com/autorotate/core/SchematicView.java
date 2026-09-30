package com.autorotate.core;

import com.autorotate.config.Config;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.util.LayerRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SchematicView {

    private SchematicView() {
    }

    public static Level world() {
        return SchematicWorldHandler.getSchematicWorld();
    }

    public static boolean isVisible(BlockPos pos) {
        if (!Config.get().respectLayerRange) {
            return true;
        }
        LayerRange range = DataManager.getRenderLayerRange();
        return range == null || range.isPositionWithinRange(pos);
    }

    public static BlockState visibleWant(BlockPos pos) {
        Level world = world();
        if (world == null) {
            return null;
        }
        return isVisible(pos) ? world.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    public static boolean wantsSolid(BlockPos pos) {
        BlockState want = visibleWant(pos);
        return want != null && !want.isAir() && !(want.getBlock() instanceof LiquidBlock);
    }

    public static boolean wantsNothing(BlockPos pos) {
        Level world = world();
        return world != null && world.getBlockState(pos).isAir();
    }
}
