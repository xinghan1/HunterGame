package com.huntergame.bedrock;

import com.huntergame.HunterGame;
import com.huntergame.vote.VoteSystem;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BedrockVoteSystemGUI {
   public static void openBedrockVoteMenu(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, HunterGame plugin, Player player) {
      BaseAPI baseAPI = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      if (baseAPI != null) {
         voteSystem.recordPlayerModeChoice(player, 2);
         SimpleForm.Builder builder = (SimpleForm.Builder)((SimpleForm.Builder)SimpleForm.builder().title(plugin.getMessage("bedrock_vote_menu_title", "&c&l游戏投票"))).content(plugin.getMessage("bedrock_vote_menu_content", "请选择你的投票：")).button(plugin.getMessage("bedrock_vote_role_button", "&a投票游戏角色")).button(plugin.getMessage("bedrock_vote_type_button", "&6投票胜利条件")).button(plugin.getMessage("bedrock_close_button", "&c关闭菜单")).validResultHandler((response) -> (new BukkitRunnable() {
            public void run() {
               switch (response.clickedButtonId()) {
                  case 0:
                     BedrockVoteSystemGUI.openBedrockRoleVote(voteSystem, voteGuiConfig, plugin, player);
                     break;
                  case 1:
                     BedrockVoteSystemGUI.openBedrockTypeVote(voteSystem, voteGuiConfig, plugin, player);
                  case 2:
               }
            }
         }).runTask(plugin));
         baseAPI.sendForm(player.getUniqueId(), builder);
      }
   }

   public static void openBedrockRoleVote(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, HunterGame plugin, Player player) {
      BaseAPI spigotMaster = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      if (spigotMaster != null) {
         ConfigurationSection roleSection = voteGuiConfig.getConfigurationSection("role-section.items");
         if (roleSection != null) {
            SimpleForm.Builder builder = ((SimpleForm.Builder)SimpleForm.builder().title(plugin.getMessage("bedrock_role_vote_title", "&b&l选择游戏角色"))).content(plugin.getMessage("bedrock_role_vote_content", "选择你的阵营（概率增加）："));
            List<Integer> roleIds = new ArrayList();
            List<ConfigurationSection> roleConfigs = voteSystem.getConfigItems(roleSection);

            for(ConfigurationSection roleCfg : roleConfigs) {
               int roleId = roleCfg.getInt("id");
               String name = ChatColor.translateAlternateColorCodes('&', roleCfg.getString("name", "未知角色"));
               int currentVotes = voteSystem.getRoleVoteCount(roleId);
               builder.button(plugin.getMessage("bedrock_vote_option_button", "%name%\n&r&a当前票数: %votes%").replace("%name%", name).replace("%votes%", String.valueOf(currentVotes)));
               roleIds.add(roleId);
            }

            builder.button(plugin.getMessage("bedrock_close_short_button", "&c关闭"));
            builder.validResultHandler((response) -> (new BukkitRunnable() {
                  public void run() {
                     int clickedId = response.clickedButtonId();
                     if (clickedId < roleIds.size()) {
                        int selectedRoleId = (Integer)roleIds.get(clickedId);
                        ConfigurationSection selectedCfg = (ConfigurationSection)roleConfigs.get(clickedId);
                        String roleName = ChatColor.translateAlternateColorCodes('&', selectedCfg.getString("name", "未知角色"));
                        voteSystem.recordPlayerRoleVote(player, selectedRoleId);
                        String key = selectedRoleId == 1 ? "choice_camp_escaper" : "choice_camp_hunter";
                        String fallback = selectedRoleId == 1 ? "&a已选择阵营: %role%" : "&c已选择阵营: %role%";
                        player.sendMessage(plugin.getMessage(key, fallback).replace("%role%", roleName));
                        voteSystem.playItemSound(player, selectedCfg);
                     }
                  }
               }).runTask(plugin));
            spigotMaster.sendForm(player.getUniqueId(), builder);
         }
      }
   }

   public static void openBedrockTypeVote(VoteSystem voteSystem, ConfigurationSection voteGuiConfig, HunterGame plugin, Player player) {
      BaseAPI spigotMaster = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      if (spigotMaster != null) {
         ConfigurationSection typeSection = voteGuiConfig.getConfigurationSection("type-section.items");
         if (typeSection != null) {
            SimpleForm.Builder builder = ((SimpleForm.Builder)SimpleForm.builder().title(plugin.getMessage("bedrock_type_vote_title", "&6&l\u9009\u62e9\u80dc\u5229\u6761\u4ef6"))).content(plugin.getMessage("bedrock_type_vote_content", "\u7968\u6570\u6700\u9ad8\u8005\u5c06\u6210\u4e3a\u672c\u5c40\u80dc\u5229\u6761\u4ef6\uff1a"));
            List<Integer> typeIds = new ArrayList();
            List<ConfigurationSection> typeConfigs = voteSystem.getConfigItems(typeSection);

            for(ConfigurationSection typeCfg : typeConfigs) {
               int typeId = typeCfg.getInt("id");
               String name = ChatColor.translateAlternateColorCodes('&', typeCfg.getString("name", "\u672a\u77e5\u7c7b\u578b"));
               int currentVotes = voteSystem.getTypeVoteCount(typeId);
               builder.button(plugin.getMessage("bedrock_vote_option_button", "%name%\n&r&a\u5f53\u524d\u7968\u6570: %votes%").replace("%name%", name).replace("%votes%", String.valueOf(currentVotes)));
               typeIds.add(typeId);
            }

            builder.button(plugin.getMessage("bedrock_back_button", "&c\u8fd4\u56de\u4e3b\u83dc\u5355"));
            builder.validResultHandler((response) -> (new BukkitRunnable() {
                  public void run() {
                     int clickedId = response.clickedButtonId();
                     if (clickedId >= typeIds.size()) {
                        BedrockVoteSystemGUI.openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
                     } else {
                        int selectedTypeId = (Integer)typeIds.get(clickedId);
                        ConfigurationSection selectedCfg = (ConfigurationSection)typeConfigs.get(clickedId);
                        String typeName = ChatColor.translateAlternateColorCodes('&', selectedCfg.getString("name", "\u672a\u77e5\u7c7b\u578b"));
                        voteSystem.recordPlayerTypeVote(player, selectedTypeId);
                        player.sendMessage(plugin.getMessage("choice", "&a\u5df2\u9009\u62e9: %modeName%").replace("%modeName%", typeName));
                        voteSystem.playItemSound(player, selectedCfg);
                        BedrockVoteSystemGUI.openBedrockVoteMenu(voteSystem, voteGuiConfig, plugin, player);
                     }
                  }
               }).runTask(plugin));
            spigotMaster.sendForm(player.getUniqueId(), builder);
         }
      }
   }
}
