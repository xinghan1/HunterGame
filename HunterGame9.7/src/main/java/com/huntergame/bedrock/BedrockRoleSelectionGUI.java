package com.huntergame.bedrock;

import com.huntergame.HunterGame;
import com.huntergame.role.RoleSelectionHandler;
import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BedrockRoleSelectionGUI {
   public static void openBedrockRoleSelection(HunterGame plugin, RoleSelectionHandler handler, Player player) {
      BaseAPI baseAPI = (BaseAPI)Bukkit.getPluginManager().getPlugin("BaseAPI");
      if (baseAPI != null) {
         String title = plugin.getGuiConfig().getString("join_midway_role-gui.title", "\u9009\u62e9\u4f60\u7684\u89d2\u8272");
         SimpleForm.Builder builder = ((SimpleForm.Builder)SimpleForm.builder().title(ChatColor.translateAlternateColorCodes('&', title))).content(plugin.getMessage("bedrock_role_selection_content", "\u6e38\u620f\u8fdb\u884c\u4e2d\uff0c\u8bf7\u9009\u62e9\u4f60\u7684\u89d2\u8272\uff1a"));
         int hunterCount = plugin.getHunters().size();
         int escaperCount = plugin.getEscapers().size();
         double ratio = escaperCount > 0 ? (double)hunterCount / (double)escaperCount : (double)hunterCount;
         boolean showEscaperOption = ratio >= handler.ESCAPER_RATIO_THRESHOLD;
         String hunterName = plugin.getGuiConfig().getString("join_midway_role-gui.hunter.name", "&c\u730e\u4eba");
         builder.button(ChatColor.translateAlternateColorCodes('&', hunterName));
         if (showEscaperOption) {
            String escaperName = plugin.getGuiConfig().getString("join_midway_role-gui.escaper.name", "&a\u9003\u751f\u8005");
            builder.button(ChatColor.translateAlternateColorCodes('&', escaperName));
         }

         String spectatorName = plugin.getGuiConfig().getString("join_midway_role-gui.spectator.name", "&7\u65c1\u89c2\u8005");
         builder.button(ChatColor.translateAlternateColorCodes('&', spectatorName));
         builder.validResultHandler((response) -> (new BukkitRunnable() {
               public void run() {
                  int id = response.clickedButtonId();
                  UUID playerId = player.getUniqueId();
                  if (id == 0) {
                     handler.selectHunterRole(player, playerId);
                  } else if (showEscaperOption) {
                     if (id == 1) {
                        handler.selectEscaperRole(player, playerId);
                     } else {
                        handler.selectSpectatorRole(player);
                     }
                  } else if (id == 1) {
                     handler.selectSpectatorRole(player);
                  }

               }
            }).runTask(plugin));
         baseAPI.sendForm(player.getUniqueId(), builder);
      }
   }
}
