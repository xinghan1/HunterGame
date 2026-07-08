package com.huntergame.gui;

import com.huntergame.HunterGame;
import com.huntergame.tracking.HunterTracker;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TrackingGUI {
   public static void openTrackingGUI(HunterGame plugin, Player player) {
      Inventory gui = Bukkit.createInventory((InventoryHolder)null, 9, plugin.getMessage("tracking_gui_title", "\u9009\u62e9\u64cd\u4f5c"));
      ItemStack teleportItem = new ItemStack(Material.ENDER_PEARL);
      ItemMeta teleportMeta = teleportItem.getItemMeta();
      teleportMeta.setDisplayName(plugin.getMessage("tracking_gui_teleport_item", "&a\u4f20\u9001\u5230\u961f\u53cb"));
      teleportItem.setItemMeta(teleportMeta);
      gui.setItem(0, teleportItem);
      ItemStack switchTrackingItem = new ItemStack(Material.COMPASS);
      ItemMeta switchTrackingMeta = switchTrackingItem.getItemMeta();
      switchTrackingMeta.setDisplayName(plugin.getMessage("tracking_gui_switch_item", "&e\u5207\u6362\u6307\u5357\u9488\u76ee\u6807"));
      switchTrackingItem.setItemMeta(switchTrackingMeta);
      gui.setItem(1, switchTrackingItem);
      player.openInventory(gui);
   }

   public static void openTeammateListGUI(HunterGame plugin, HunterTracker tracker, Player player) {
      Inventory gui = Bukkit.createInventory((InventoryHolder)null, 9, plugin.getMessage("tracking_teammate_gui_title", "\u9009\u62e9\u961f\u53cb"));
      List<Player> teammates = plugin.getHunters();
      teammates.remove(player);
      int slot = 0;

      for(Player teammate : teammates) {
         if (slot >= 9) {
            break;
         }

         if (teammate != null && teammate.isOnline() && plugin.isHunter(teammate.getUniqueId())) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getMessage("tracking_teammate_item", "&e%player%").replace("%player%", teammate.getName()));
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
         }
      }

      player.openInventory(gui);
   }
}
