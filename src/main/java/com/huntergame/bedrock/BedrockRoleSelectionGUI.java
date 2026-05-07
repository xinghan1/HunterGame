package com.huntergame.bedrock;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BedrockRoleSelectionGUI {

    /**
     * 打开基岩版角色选择表单
     */
    public static void openBedrockRoleSelection(HunterGame plugin, RoleSelectionHandler handler, Player player) {
        BaseAPI baseAPI = (BaseAPI) Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (baseAPI == null) {
            return;
        }

        String title = plugin.getGuiConfig().getString(
                "join_midway_role-gui.title",
                "选择你的角色"
        );

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(ChatColor.translateAlternateColorCodes('&', title))
                .content(plugin.getMessage("bedrock_role_selection_content", "游戏进行中，请选择你的角色："));

        // 1. 计算比例决定是否显示逃生者
        int hunterCount = plugin.getHunters().size();
        int escaperCount = plugin.getEscapers().size();
        double ratio = (escaperCount > 0) ? (double) hunterCount / escaperCount : hunterCount;
        boolean showEscaperOption = ratio >= handler.ESCAPER_RATIO_THRESHOLD;

        // 按钮顺序： 0: 猎人, 1: 逃生者(如果有), 2(或1): 旁观者
        String hunterName = plugin.getGuiConfig().getString("join_midway_role-gui.hunter.name", "&c猎人");
        builder.button(ChatColor.translateAlternateColorCodes('&', hunterName)); // ID 0

        final boolean finalShowEscaper = showEscaperOption;

        if (showEscaperOption) {
            String escaperName = plugin.getGuiConfig().getString("join_midway_role-gui.escaper.name", "&a逃生者");
            builder.button(ChatColor.translateAlternateColorCodes('&', escaperName)); // ID 1
        }

        String spectatorName = plugin.getGuiConfig().getString("join_midway_role-gui.spectator.name", "&7旁观者");
        builder.button(ChatColor.translateAlternateColorCodes('&', spectatorName)); // ID 2 或 1

        // 3. 处理点击
        builder.validResultHandler(response -> {
            // 切换回主线程处理游戏逻辑
            new BukkitRunnable() {
                @Override
                public void run() {
                    int id = response.clickedButtonId();
                    UUID playerId = player.getUniqueId();

                    // 逻辑判断：根据是否显示了逃生者按钮来推断 ID 对应的功能
                    if (id == 0) {
                        // 点击了猎人
                        handler.selectHunterRole(player, playerId);
                    } else if (finalShowEscaper) {
                        // 如果显示了逃生者按钮
                        if (id == 1) {
                            handler.selectEscaperRole(player, playerId);
                        } else {
                            // id == 2
                            handler.selectSpectatorRole(player);
                        }
                    } else {
                        // 如果没有显示逃生者按钮，那么 ID 1 就是旁观者
                        if (id == 1) {
                            handler.selectSpectatorRole(player);
                        }
                    }
                }
            }.runTask(plugin);
        });

        baseAPI.sendForm(player.getUniqueId(), builder);
    }
}
