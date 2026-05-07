package com.huntergame.inventory;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.UUID;

public class SharedBackpackManager implements Listener {

    public static final int BACKPACK_SLOT = 8;
    public static final int INVENTORY_SIZE = 27;

    private final HunterGame plugin;
    private final Inventory hunterInventory;
    private final Inventory escaperInventory;
    private final ItemStack hunterBackpackItem;
    private final ItemStack escaperBackpackItem;

    public SharedBackpackManager(HunterGame plugin) {
        this.plugin = plugin;
        String hunterBackpackName = plugin.getMessage("hunter_backpack_name", "&c猎人共享背包");
        String escaperBackpackName = plugin.getMessage("escaper_backpack_name", "&9逃生者共享背包");
        this.hunterInventory = Bukkit.createInventory(null, INVENTORY_SIZE, hunterBackpackName);
        this.escaperInventory = Bukkit.createInventory(null, INVENTORY_SIZE, escaperBackpackName);
        this.hunterBackpackItem = createBackpackItem(hunterBackpackName, plugin.getMessage("hunter_backpack_lore", "&7右键打开猎人共享背包"));
        this.escaperBackpackItem = createBackpackItem(escaperBackpackName, plugin.getMessage("escaper_backpack_lore", "&7右键打开逃生者共享背包"));
    }

    public Inventory getHunterInventory() {
        return hunterInventory;
    }

    public Inventory getEscaperInventory() {
        return escaperInventory;
    }

    public void clearBackpacks() {
        hunterInventory.clear();
        escaperInventory.clear();
    }

    public void giveBackpack(Player player, boolean hunter) {
        ItemStack backpack = hunter ? hunterBackpackItem : escaperBackpackItem;
        player.getInventory().setItem(BACKPACK_SLOT, backpack.clone());
    }

    public boolean isHunterBackpack(ItemStack item) {
        return hasDisplayName(item, plugin.getMessage("hunter_backpack_name", "&c猎人共享背包"));
    }

    public boolean isEscaperBackpack(ItemStack item) {
        return hasDisplayName(item, plugin.getMessage("escaper_backpack_name", "&9逃生者共享背包"));
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        if (plugin.isHunter(playerId) && isHunterBackpack(droppedItem)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("hunter_prohibit_discard", "&c猎人共享背包不能丢弃！"));
            return;
        }

        if (!plugin.isHunter(playerId) && isEscaperBackpack(droppedItem)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("escape_prohibit_discard", "&c逃生者共享背包不能丢弃！"));
        }
    }

    private ItemStack createBackpackItem(String displayName, String lore) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasDisplayName(ItemStack item, String displayName) {
        if (item == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && displayName.equals(meta.getDisplayName());
    }
}

