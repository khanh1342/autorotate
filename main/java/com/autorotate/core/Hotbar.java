package com.autorotate.core;

import com.autorotate.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Hotbar {

    public enum Supply {
        HELD,
        PULLED,
        MISSING
    }

    private static final int HOTBAR_SIZE = Inventory.getSelectionSize();
    private static final int MAIN_INVENTORY_END = Inventory.INVENTORY_SIZE;
    private static final long[] LAST_USED = new long[HOTBAR_SIZE];

    private static long clock;

    private Hotbar() {
    }

    public static Supply ensure(LocalPlayer player, InteractionHand hand, Item item) {
        if (item == Items.AIR) {
            return Supply.MISSING;
        }
        if (player.getItemInHand(hand).is(item)) {
            return Supply.HELD;
        }
        Config config = Config.get();
        if (!config.autoSwitchHotbar || hand != InteractionHand.MAIN_HAND) {
            return Supply.MISSING;
        }
        Inventory inventory = player.getInventory();
        int hotbarSlot = find(inventory, item, 0, HOTBAR_SIZE);
        if (hotbarSlot >= 0) {
            select(inventory, hotbarSlot);
            return Supply.HELD;
        }
        if (config.swapFromInventory) {
            int inventorySlot = find(inventory, item, HOTBAR_SIZE, MAIN_INVENTORY_END);
            if (inventorySlot >= 0) {
                return pull(player, inventorySlot, item) ? Supply.PULLED : Supply.MISSING;
            }
        }
        return player.hasInfiniteMaterials() && conjure(player, item) ? Supply.HELD : Supply.MISSING;
    }

    private static boolean pull(LocalPlayer player, int inventorySlot, Item item) {
        Inventory inventory = player.getInventory();
        int destination = destinationSlot(inventory);
        Minecraft.getInstance().gameMode.handleInventoryMouseClick(
            player.inventoryMenu.containerId, inventorySlot, destination, ClickType.SWAP, player);
        closeInventory(player);
        select(inventory, destination);
        Debug.log("Pulled {} from slot {} into hotbar {}", item, inventorySlot, destination);
        return inventory.getItem(destination).is(item);
    }

    private static void closeInventory(LocalPlayer player) {
        if (Minecraft.getInstance().screen == null && player.containerMenu == player.inventoryMenu) {
            player.connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
        }
    }

    private static boolean conjure(LocalPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int destination = destinationSlot(inventory);
        ItemStack stack = item.getDefaultInstance();
        inventory.setItem(destination, stack);
        Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(stack, InventoryMenu.USE_ROW_SLOT_START + destination);
        select(inventory, destination);
        Debug.log("Took {} from the creative inventory into hotbar {}", item, destination);
        return true;
    }

    public static boolean has(LocalPlayer player, Item item) {
        return find(player.getInventory(), item, 0, MAIN_INVENTORY_END) >= 0;
    }

    public static void markUsed(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            LAST_USED[slot] = ++clock;
        }
    }

    private static void select(Inventory inventory, int slot) {
        inventory.setSelectedSlot(slot);
        markUsed(slot);
    }

    private static int destinationSlot(Inventory inventory) {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        markUsed(inventory.getSelectedSlot());
        int leastRecent = -1;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            boolean holdsBlock = inventory.getItem(slot).getItem() instanceof BlockItem;
            if (holdsBlock && (leastRecent < 0 || LAST_USED[slot] < LAST_USED[leastRecent])) {
                leastRecent = slot;
            }
        }
        return leastRecent >= 0 ? leastRecent : inventory.getSelectedSlot();
    }

    private static int find(Inventory inventory, Item item, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }
}
