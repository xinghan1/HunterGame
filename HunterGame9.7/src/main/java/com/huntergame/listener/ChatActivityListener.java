package com.huntergame.listener;

import com.huntergame.HunterGame;
import java.util.UUID;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class ChatActivityListener implements Listener {
   private static final String[] CHAT_FORMAT_PRIORITY = new String[]{"admin", "mvp", "vip", "default"};
   private final HunterGame plugin;
   private final InactivityMonitor inactivityDetection;

   public ChatActivityListener(HunterGame plugin, InactivityMonitor inactivityDetection) {
      this.plugin = plugin;
      this.inactivityDetection = inactivityDetection;
   }

   @EventHandler
   public void onPlayerChat(AsyncPlayerChatEvent event) {
      if (this.plugin.getConfig().getBoolean("formats.enable", false)) {
         Player player = event.getPlayer();
         UUID playerId = player.getUniqueId();
         this.inactivityDetection.updateActivity(player);
         String format = this.resolveChatFormat(player);
         format = format.replace("%player%", player.getDisplayName()).replace("%message%", event.getMessage());
         if (this.plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            format = PlaceholderAPI.setPlaceholders(player, format);
         }

         format = ChatColor.translateAlternateColorCodes('&', format);
         event.setCancelled(true);
         if (!this.plugin.isGameRunning()) {
            this.broadcastToAll(format);
         } else {
            this.sendTeamChat(player, playerId, format);
         }
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Location to = event.getTo();
      if (to != null && (event.getFrom().getBlockX() != to.getBlockX() || event.getFrom().getBlockY() != to.getBlockY() || event.getFrom().getBlockZ() != to.getBlockZ())) {
         this.inactivityDetection.updateActivity(event.getPlayer());
      }
   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      this.inactivityDetection.updateActivity(event.getPlayer());
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      this.inactivityDetection.updateActivity(event.getPlayer());
   }

   private String resolveChatFormat(Player player) {
      for(String key : CHAT_FORMAT_PRIORITY) {
         String path = "formats." + key;
         if (this.plugin.getConfig().contains(path)) {
            String permission = this.plugin.getConfig().getString(path + ".permission");
            if (permission == null || permission.isEmpty() || player.hasPermission(permission)) {
               String format = this.plugin.getConfig().getString(path + ".format");
               if (format != null && !format.isEmpty()) {
                  return format;
               }
            }
         }
      }

      return "&7[%huntergame_proficiency%\u2605]%huntergame_role% &f%player%: %message%";
   }

   private void broadcastToAll(String message) {
      for(Player recipient : Bukkit.getOnlinePlayers()) {
         recipient.sendMessage(message);
      }

   }

   private void sendTeamChat(Player sender, UUID senderId, String message) {
      boolean senderIsHunter = this.plugin.isHunter(senderId);
      boolean senderIsEscaper = this.plugin.isEscaper(senderId);
      boolean senderIsSpectator = sender.getGameMode() == GameMode.SPECTATOR;

      for(Player recipient : Bukkit.getOnlinePlayers()) {
         UUID recipientId = recipient.getUniqueId();
         boolean recipientIsSpectator = recipient.getGameMode() == GameMode.SPECTATOR;
         if (senderIsSpectator && recipientIsSpectator) {
            recipient.sendMessage(message);
         } else if (!senderIsHunter || !this.plugin.isHunter(recipientId) && !recipientIsSpectator) {
            if (senderIsEscaper && (this.plugin.isEscaper(recipientId) || recipientIsSpectator)) {
               recipient.sendMessage(message);
            }
         } else {
            recipient.sendMessage(message);
         }
      }

   }
}
