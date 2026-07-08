package com.huntergame.motd;

import com.huntergame.HunterGame;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class MotdListener implements Listener {
   private final HunterGame plugin;

   public MotdListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onServerListPing(ServerListPingEvent event) {
      String motd = this.getCurrentMOTD();
      event.setMotd(ChatColor.translateAlternateColorCodes('&', motd));
   }

   public String getCurrentMOTD() {
      if (this.plugin.isResetting()) {
         return this.plugin.getMessage("motd.resetting", "&c\u91cd\u7f6e\u4e2d");
      } else if (this.plugin.isGameRunning()) {
         return this.plugin.getMessage("motd.game-running", "&c\u6e38\u620f\u4e2d");
      } else {
         return this.plugin.isGameEnded() ? this.plugin.getMessage("motd.ended", "&6\u5df2\u7ed3\u675f") : this.plugin.getMessage("motd.waiting", "&a\u7b49\u5f85\u4e2d");
      }
   }
}
