package com.huntergame.gui;

import com.huntergame.vote.VoteSystem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VoteSystemGUI {
   public static void openVoteGUI(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, Player player) {
      if (player != null && player.isOnline()) {
         if (voteSystem.isFixedModeEnabled()) {
            Object[] fixedModeInfo = voteSystem.getFixedModeInfo();
            int fixedModeId = (Integer)fixedModeInfo[0];
            voteSystem.recordPlayerModeChoice(player, fixedModeId);
         }

         String title = ChatColor.translateAlternateColorCodes('&', voteGuiConfig.getString("title", "\u6a21\u5f0f\u4e0e\u89d2\u8272\u9009\u62e9"));
         int size = voteGuiConfig.getInt("size", 27);
         if (size % 9 != 0 || size < 27) {
            size = 36;
         }

         Inventory gui = Bukkit.createInventory((InventoryHolder)null, size, title);
         voteSystem.addTypeItemsToGUI(gui);
         voteSystem.addModeItemsToGUI(gui);
         voteSystem.addRoleItemsToGUI(gui);
         voteSystem.fillDividerItems(gui);
         player.openInventory(gui);
      }
   }
}
