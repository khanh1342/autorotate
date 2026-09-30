package com.autorotate.core;

import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class Interactions {

    private static final List<Class<? extends Block>> INTERACTIVE = List.of(
        DiodeBlock.class,
        LeverBlock.class,
        ButtonBlock.class,
        DoorBlock.class,
        TrapDoorBlock.class,
        FenceGateBlock.class,
        NoteBlock.class,
        CraftingTableBlock.class,
        AnvilBlock.class,
        LoomBlock.class,
        CartographyTableBlock.class,
        GrindstoneBlock.class,
        StonecutterBlock.class,
        SmithingTableBlock.class,
        EnchantingTableBlock.class,
        CakeBlock.class,
        ComposterBlock.class,
        FlowerPotBlock.class,
        DaylightDetectorBlock.class,
        BedBlock.class,
        RespawnAnchorBlock.class,
        DragonEggBlock.class);

    private Interactions() {
    }

    public static boolean isInteractive(BlockState state) {
        if (state.hasBlockEntity()) {
            return true;
        }
        Block block = state.getBlock();
        for (Class<? extends Block> type : INTERACTIVE) {
            if (type.isInstance(block)) {
                return true;
            }
        }
        return false;
    }
}
