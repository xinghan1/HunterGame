package com.huntergame.bedrock;

import com.huntergame.HunterGame;
import com.huntergame.tracking.HunterTracker;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BedrockTrackingGUI {
   public static void openBedrockTrackingMenu(HunterGame plugin, HunterTracker tracker, Player player) {
      BaseAPI baseAPI = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      if (baseAPI != null) {
         SimpleForm.Builder builder = ((SimpleForm.Builder)SimpleForm.builder().title(plugin.getMessage("bedrock_tracking_title", "\u730e\u4eba\u8ffd\u8e2a\u5668"))).content(plugin.getMessage("bedrock_tracking_content", "\u8bf7\u9009\u62e9\u64cd\u4f5c\uff1a")).button(plugin.getMessage("bedrock_tracking_teleport_button", "&a\u4f20\u9001\u5230\u961f\u53cb\n&7\u6d88\u8017\u751f\u547d\u503c\u5feb\u901f\u652f\u63f4")).button(plugin.getMessage("bedrock_tracking_switch_button", "&e\u5207\u6362\u6307\u5357\u9488\u76ee\u6807\n&7\u8ffd\u8e2a\u6700\u8fd1\u9003\u751f\u8005/\u961f\u53cb")).button(plugin.getMessage("bedrock_close_button", "&c\u5173\u95ed\u83dc\u5355"));
         builder.validResultHandler((response) -> (new BukkitRunnable() {
               public void run() {
                  switch (response.clickedButtonId()) {
                     case 0:
                        if (tracker.isEscaperNearby(player, (double)tracker.DETECTION_DISTANCE)) {
                           player.sendMessage(plugin.getMessage("nearby_escape", "&c\u9644\u8fd1\u6709\u9003\u751f\u8005\uff0c\u65e0\u6cd5\u4f20\u9001\uff01"));
                           return;
                        }

                        BedrockTrackingGUI.openBedrockTeammateList(plugin, tracker, player);
                        break;
                     case 1:
                        tracker.switchToHunterTrackingTarget(player);
                     case 2:
                  }

               }
            }).runTask(plugin));
         baseAPI.sendForm(player.getUniqueId(), builder);
      }
   }

   public static void openBedrockTeammateList(HunterGame plugin, HunterTracker tracker, Player player) {
      BaseAPI spigotMaster = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      if (spigotMaster != null) {
         List<Player> teammates = plugin.getHunters();
         teammates.remove(player);
         if (teammates.isEmpty()) {
            player.sendMessage(plugin.getMessage("no_teammates_to_teleport", "&c\u6ca1\u6709\u53ef\u4f20\u9001\u7684\u961f\u53cb\uff01"));
         } else {
            SimpleForm.Builder builder = ((SimpleForm.Builder)SimpleForm.builder().title(plugin.getMessage("bedrock_teammate_list_title", "\u9009\u62e9\u4f20\u9001\u76ee\u6807"))).content(plugin.getMessage("bedrock_teammate_list_content", "\u70b9\u51fb\u961f\u53cb\u5934\u50cf\u8fdb\u884c\u4f20\u9001\uff08\u6d88\u8017 %health% \u8840\u91cf\uff09\uff1a").replace("%health%", String.valueOf(tracker.DEDUCT_HEALTH)));
            List<Player> validTeammates = new ArrayList();

            for(Player teammate : teammates) {
               if (teammate != null && teammate.isOnline() && plugin.isHunter(teammate.getUniqueId())) {
                  builder.button(plugin.getMessage("bedrock_teammate_button", "&e%player%\n&7\u70b9\u51fb\u4f20\u9001").replace("%player%", teammate.getName()));
                  validTeammates.add(teammate);
               }
            }

            builder.button(plugin.getMessage("bedrock_cancel_button", "&c\u53d6\u6d88"));
            builder.validResultHandler((response) -> (new BukkitRunnable() {
                  public void run() {
                     int id = response.clickedButtonId();
                     if (id < validTeammates.size()) {
                        Player target = (Player)validTeammates.get(id);
                        if (tracker.isOnCooldown(player)) {
                           long timeLeft = tracker.getCooldownTimeLeft(player);
                           player.sendMessage(plugin.getMessage("waiting_countdown_teleport", "&c\u51b7\u5374\u4e2d: " + timeLeft + "s"));
                        } else {
                           if (target != null && target.isOnline()) {
                              tracker.performTeleport(player, target);
                           } else {
                              player.sendMessage(plugin.getMessage("Teammate_unavailable", "&c\u76ee\u6807\u4e0d\u53ef\u7528"));
                           }

                        }
                     }
                  }
               }).runTask(plugin));
            spigotMaster.sendForm(player.getUniqueId(), builder);
         }
      }
   }
}
