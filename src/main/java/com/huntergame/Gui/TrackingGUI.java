package com.huntergame.gui;

import com.huntergame.HunterGame;
import com.huntergame.tracking.HunterTracker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class TrackingGUI {

    /**
     * 打开猎人追踪操作 GUI
     */
    public static void openTrackingGUI(HunterGame plugin, Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, plugin.getMessage("tracking_gui_title", "选择操作"));

        ItemStack teleportItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportMeta = teleportItem.getItemMeta();
        teleportMeta.setDisplayName(plugin.getMessage("tracking_gui_teleport_item", "&a传送到队友"));
        teleportItem.setItemMeta(teleportMeta);
        gui.setItem(0, teleportItem);

        ItemStack switchTrackingItem = new ItemStack(Material.COMPASS);
        ItemMeta switchTrackingMeta = switchTrackingItem.getItemMeta();
        switchTrackingMeta.setDisplayName(plugin.getMessage("tracking_gui_switch_item", "&e切换指南针目标"));
        switchTrackingItem.setItemMeta(switchTrackingMeta);
        gui.setItem(1, switchTrackingItem);

        player.openInventory(gui);
    }

    /**
     * 打开队友列表 GUI
     */
    public static void openTeammateListGUI(HunterGame plugin, HunterTracker tracker, Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, plugin.getMessage("tracking_teammate_gui_title", "选择队友"));
        List<Player> teammates = plugin.getHunters();
        teammates.remove(player); // 列表中移除自己

        int slot = 0;
        for (Player teammate : teammates) {
            if (slot >= 9) break; // 防止超出格子
            if (teammate != null && teammate.isOnline() && plugin.isHunter(teammate.getUniqueId())) {
                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(plugin.getMessage("tracking_teammate_item", "&e%player%")
                        .replace("%player%", teammate.getName()));
                item.setItemMeta(meta);
                gui.setItem(slot++, item);
            }
        }
        player.openInventory(gui);
    }
}
