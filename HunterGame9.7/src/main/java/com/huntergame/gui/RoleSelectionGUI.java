package com.huntergame.gui;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RoleSelectionGUI {
   public static void openRoleSelectionGUI(HunterGame plugin, RoleSelectionHandler handler, Player player) {
      String guiTitle = plugin.getGuiConfig().getString("join_midway_role-gui.title", "\u9009\u62e9\u4f60\u7684\u89d2\u8272");
      Inventory gui = Bukkit.createInventory(player, 9, ChatColor.translateAlternateColorCodes('&', guiTitle));
      int hunterCount = plugin.getHunters().size();
      int escaperCount = plugin.getEscapers().size();
      double ratio = escaperCount > 0 ? (double)hunterCount / (double)escaperCount : (double)hunterCount;
      boolean showEscaperOption = ratio >= handler.ESCAPER_RATIO_THRESHOLD;
      Material hunterMat = Material.valueOf(plugin.getGuiConfig().getString("join_midway_role-gui.hunter.material", "IRON_SWORD"));
      String hunterName = ChatColor.translateAlternateColorCodes('&', plugin.getGuiConfig().getString("join_midway_role-gui.hunter.name", "&c\u730e\u4eba"));
      List<String> hunterLore = (List)plugin.getGuiConfig().getStringList("join_midway_role-gui.hunter.lore").stream().map((line) -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
      ItemStack hunterItem = createGuiItem(hunterMat, hunterName, (String[])hunterLore.toArray(new String[0]));
      gui.setItem(plugin.getGuiConfig().getInt("join_midway_role-gui.hunter.slot", 3), hunterItem);
      if (showEscaperOption) {
         Material escaperMat = Material.valueOf(plugin.getGuiConfig().getString("join_midway_role-gui.escaper.material", "EMERALD"));
         String escaperName = ChatColor.translateAlternateColorCodes('&', plugin.getGuiConfig().getString("join_midway_role-gui.escaper.name", "&a\u9003\u751f\u8005"));
         List<String> escaperLore = (List)plugin.getGuiConfig().getStringList("join_midway_role-gui.escaper.lore").stream().map((line) -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
         ItemStack escaperItem = createGuiItem(escaperMat, escaperName, (String[])escaperLore.toArray(new String[0]));
         gui.setItem(plugin.getGuiConfig().getInt("join_midway_role-gui.escaper.slot", 4), escaperItem);
      }

      Material specMat = Material.valueOf(plugin.getGuiConfig().getString("join_midway_role-gui.spectator.material", "FEATHER"));
      String specName = ChatColor.translateAlternateColorCodes('&', plugin.getGuiConfig().getString("join_midway_role-gui.spectator.name", "&7\u65c1\u89c2\u8005"));
      List<String> specLore = (List)plugin.getGuiConfig().getStringList("join_midway_role-gui.spectator.lore").stream().map((line) -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
      ItemStack spectatorItem = createGuiItem(specMat, specName, (String[])specLore.toArray(new String[0]));
      gui.setItem(plugin.getGuiConfig().getInt("join_midway_role-gui.spectator.slot", 5), spectatorItem);
      player.openInventory(gui);
   }

   private static ItemStack createGuiItem(Material material, String name, String... lore) {
      ItemStack item = new ItemStack(material, 1);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         if (lore != null && lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
         }

         item.setItemMeta(meta);
      }

      return item;
   }
}
