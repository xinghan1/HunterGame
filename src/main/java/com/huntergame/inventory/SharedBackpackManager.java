package com.huntergame.inventory;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    public static final String HUNTER_BACKPACK_NAME = ChatColor.RED + "猎人共享背包";
    public static final String ESCAPER_BACKPACK_NAME = ChatColor.BLUE + "逃生者共享背包";

    private final HunterGame plugin;
    private final Inventory hunterInventory;
    private final Inventory escaperInventory;
    private final ItemStack hunterBackpackItem;
    private final ItemStack escaperBackpackItem;

    public SharedBackpackManager(HunterGame plugin) {
        this.plugin = plugin;
        this.hunterInventory = Bukkit.createInventory(null, INVENTORY_SIZE, HUNTER_BACKPACK_NAME);
        this.escaperInventory = Bukkit.createInventory(null, INVENTORY_SIZE, ESCAPER_BACKPACK_NAME);
        this.hunterBackpackItem = createBackpackItem(HUNTER_BACKPACK_NAME, ChatColor.GRAY + "右键打开猎人共享背包");
        this.escaperBackpackItem = createBackpackItem(ESCAPER_BACKPACK_NAME, ChatColor.GRAY + "右键打开逃生者共享背包");
    }

    public Inventory getHunterInventory() {
        return hunterInventory;
    }

    public Inventory getEscaperInventory() {
        return escaperInventory;
    }

    public void giveBackpack(Player player, boolean hunter) {
        ItemStack backpack = hunter ? hunterBackpackItem : escaperBackpackItem;
        player.getInventory().setItem(BACKPACK_SLOT, backpack.clone());
    }

    public boolean isHunterBackpack(ItemStack item) {
        return hasDisplayName(item, HUNTER_BACKPACK_NAME);
    }

    public boolean isEscaperBackpack(ItemStack item) {
        return hasDisplayName(item, ESCAPER_BACKPACK_NAME);
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
