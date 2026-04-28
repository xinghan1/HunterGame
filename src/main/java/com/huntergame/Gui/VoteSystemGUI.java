package com.huntergame.Gui;

import com.huntergame.votesystem.VoteSystem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class VoteSystemGUI {

    /**
     * 打开模式与角色选择GUI
     */
    public static void openVoteGUI(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, Player player) {
        if (player == null || !player.isOnline()) return;

        if (voteSystem.isFixedModeEnabled()) {
            Object[] fixedModeInfo = voteSystem.getFixedModeInfo();
            int fixedModeId = (int) fixedModeInfo[0];
            // 自动为玩家分配固定模式（无需手动选择）
            voteSystem.recordPlayerModeChoice(player, fixedModeId);
        }

        // 从配置读取GUI基础属性
        String title = ChatColor.translateAlternateColorCodes('&', voteGuiConfig.getString("title", "模式与角色选择"));
        int size = voteGuiConfig.getInt("size", 27);
        // 确保是9的倍数且至少为27
        if (size % 9 != 0 || size < 27) size = 36;

        // 创建GUI
        Inventory gui = Bukkit.createInventory(null, size, title);

        // 1. 添加战役类型物品 (持久战/通关战) - 放在第一行
        voteSystem.addTypeItemsToGUI(gui);

        // 2. 添加模式选择物品 - 放在第二行
        voteSystem.addModeItemsToGUI(gui);

        // 3. 添加阵营选择物品 - 放在第三行
        voteSystem.addRoleItemsToGUI(gui);

        // 4. 填充分隔装饰
        voteSystem.fillDividerItems(gui);

        // 打开GUI
        player.openInventory(gui);
    }
}
