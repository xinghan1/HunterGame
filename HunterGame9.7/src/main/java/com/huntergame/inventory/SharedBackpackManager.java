package com.huntergame.inventory;

import com.huntergame.HunterGame;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
      String hunterBackpackName = plugin.getMessage("hunter_backpack_name", "&c\u730e\u4eba\u5171\u4eab\u80cc\u5305");
      String escaperBackpackName = plugin.getMessage("escaper_backpack_name", "&9\u9003\u751f\u8005\u5171\u4eab\u80cc\u5305");
      this.hunterInventory = Bukkit.createInventory((InventoryHolder)null, 27, hunterBackpackName);
      this.escaperInventory = Bukkit.createInventory((InventoryHolder)null, 27, escaperBackpackName);
      this.hunterBackpackItem = this.createBackpackItem(hunterBackpackName, plugin.getMessage("hunter_backpack_lore", "&7\u53f3\u952e\u6253\u5f00\u730e\u4eba\u5171\u4eab\u80cc\u5305"));
      this.escaperBackpackItem = this.createBackpackItem(escaperBackpackName, plugin.getMessage("escaper_backpack_lore", "&7\u53f3\u952e\u6253\u5f00\u9003\u751f\u8005\u5171\u4eab\u80cc\u5305"));
   }

   public Inventory getHunterInventory() {
      return this.hunterInventory;
   }

   public Inventory getEscaperInventory() {
      return this.escaperInventory;
   }

   public void clearBackpacks() {
      this.hunterInventory.clear();
      this.escaperInventory.clear();
   }

   public void giveBackpack(Player player, boolean hunter) {
      ItemStack backpack = hunter ? this.hunterBackpackItem : this.escaperBackpackItem;
      player.getInventory().setItem(8, backpack.clone());
   }

   public boolean isHunterBackpack(ItemStack item) {
      return this.hasDisplayName(item, this.plugin.getMessage("hunter_backpack_name", "&c\u730e\u4eba\u5171\u4eab\u80cc\u5305"));
   }

   public boolean isEscaperBackpack(ItemStack item) {
      return this.hasDisplayName(item, this.plugin.getMessage("escaper_backpack_name", "&9\u9003\u751f\u8005\u5171\u4eab\u80cc\u5305"));
   }

   @EventHandler
   public void onPlayerDropItem(PlayerDropItemEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      ItemStack droppedItem = event.getItemDrop().getItemStack();
      if (this.plugin.isHunter(playerId) && this.isHunterBackpack(droppedItem)) {
         event.setCancelled(true);
         player.sendMessage(this.plugin.getMessage("hunter_prohibit_discard", "&c\u730e\u4eba\u5171\u4eab\u80cc\u5305\u4e0d\u80fd\u4e22\u5f03\uff01"));
      } else {
         if (!this.plugin.isHunter(playerId) && this.isEscaperBackpack(droppedItem)) {
            event.setCancelled(true);
            player.sendMessage(this.plugin.getMessage("escape_prohibit_discard", "&c\u9003\u751f\u8005\u5171\u4eab\u80cc\u5305\u4e0d\u80fd\u4e22\u5f03\uff01"));
         }

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
      } else {
         ItemMeta meta = item.getItemMeta();
         return meta != null && meta.hasDisplayName() && displayName.equals(meta.getDisplayName());
      }
   }
}
