package com.huntergame.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;

/**
 * Clears every inventory location in which a player can keep an item.
 * PlayerInventory#clear() does not include the personal crafting matrix.
 */
public final class PlayerInventoryCleaner {
    private PlayerInventoryCleaner() {
    }

    public static void clearAll(Player player) {
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (topInventory instanceof CraftingInventory) {
            topInventory.clear();
        }

        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.closeInventory();
        player.updateInventory();
    }
}
