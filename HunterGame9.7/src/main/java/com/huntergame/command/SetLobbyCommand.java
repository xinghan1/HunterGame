package com.huntergame.command;

import com.huntergame.HunterGame;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetLobbyCommand {
   private final HunterGame plugin;

   public SetLobbyCommand(HunterGame plugin) {
      this.plugin = plugin;
   }

   public void setLobby(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage(this.plugin.getMessage("non_player", "\u53ea\u6709\u73a9\u5bb6\u624d\u80fd\u6267\u884c\u8fd9\u4e2a\u547d\u4ee4\uff01"));
      } else {
         Location location = player.getLocation();
         if (location.getWorld() == null) {
            player.sendMessage(this.plugin.getMessage("world_not_found", "&c\u65e0\u6cd5\u83b7\u53d6\u5f53\u524d\u4f4d\u7f6e\u7684\u4e16\u754c\uff01"));
         } else {
            this.plugin.getConfig().set("lobby.world", location.getWorld().getName());
            this.plugin.getConfig().set("lobby.x", location.getX());
            this.plugin.getConfig().set("lobby.y", location.getY());
            this.plugin.getConfig().set("lobby.z", location.getZ());
            this.plugin.saveConfig();
            player.sendMessage(this.plugin.getMessage("set_lobby", "&a\u5927\u5385\u4f4d\u7f6e\u5df2\u6210\u529f\u8bbe\u7f6e\u4e3a\uff1a\n &7\u4e16\u754c: %world% \n &7\u5750\u6807: %coordinates%").replace("%world%", location.getWorld().getName()).replace("%coordinates%", String.format("%.2f, %.2f, %.2f", location.getX(), location.getY(), location.getZ())));
         }
      }
   }
}
