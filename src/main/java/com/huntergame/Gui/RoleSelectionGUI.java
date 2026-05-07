package com.huntergame.gui;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RoleSelectionGUI {

    /**
     * 打开角色选择界面 (Java 版)
     */
    public static void openRoleSelectionGUI(HunterGame plugin, RoleSelectionHandler handler, Player player) {
        String guiTitle = plugin.getGuiConfig().getString(
                "join_midway_role-gui.title",
                "选择你的角色"
        );
        Inventory gui = Bukkit.createInventory(player, 9, ChatColor.translateAlternateColorCodes('&', guiTitle));

        int hunterCount = plugin.getHunters().size();
        int escaperCount = plugin.getEscapers().size();
        double ratio = (escaperCount > 0) ? (double) hunterCount / escaperCount : hunterCount;
        boolean showEscaperOption = ratio >= handler.ESCAPER_RATIO_THRESHOLD;

        // 猎人物品
        Material hunterMat = Material.valueOf(plugin.getGuiConfig().getString("join_midway_role-gui.hunter.material", "IRON_SWORD"));
        String hunterName = ChatColor.translateAlternateColorCodes('&', plugin.getGuiConfig().getString("join_midway_role-gui.hunter.name", "&c猎人"));
        List<String> hunterLore = plugin.getGuiConfig().getStringList("join_midway_role-gui.hunter.lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
        ItemStack hunterItem = createGuiItem(hunterMat, hunterName, hunterLore.toArray(new String[0]));
        gui.setItem(plugin.getGuiConfig().getInt("join_midway_role-gui.hunter.slot", 3), hunterItem);

        // 逃生者物品 (如果允许)
        if (showEscaperOption) {
            Material escaperMat = Material.valueOf(plugin.getGuiConfig().getString("join_midway_role-gui.escaper.material", "EMERALD"));
            String escaperName = ChatColor.translateAlternateColorCodes('&', plugin.getGuiConfig().getString("join_midway_role-gui.escaper.name", "&a逃生者"));
            List<String> escaperLore = plugin.getGuiConfig().getStringList("join_midway_role-gui.escaper.lore").stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
            ItemStack escaperItem = createGuiItem(escaperMat, escaperName, escaperLore.toArray(new String[0]));
            gui.setItem(plugin.getGuiConfig().getInt("join_midway_role-gui.escaper.slot", 4), escaperItem);
        }

        // 旁观者物品
        Material specMat = Material.valueOf(plugin.getGuiConfig().getString("join_midway_role-gui.spectator.material", "FEATHER"));
        String specName = ChatColor.translateAlternateColorCodes('&', plugin.getGuiConfig().getString("join_midway_role-gui.spectator.name", "&7旁观者"));
        List<String> specLore = plugin.getGuiConfig().getStringList("join_midway_role-gui.spectator.lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
        ItemStack spectatorItem = createGuiItem(specMat, specName, specLore.toArray(new String[0]));
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
