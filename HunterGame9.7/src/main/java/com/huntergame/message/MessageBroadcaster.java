package com.huntergame.message;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class MessageBroadcaster {
   private final JavaPlugin plugin;
   private final List<String> messages = new ArrayList();
   private int currentIndex = 0;
   private BukkitRunnable broadcastTask;
   private int messageInterval = 240;

   public MessageBroadcaster(JavaPlugin plugin) {
      this.plugin = plugin;
      this.loadConfig();
      if (this.isEnabled()) {
         this.startBroadcasting();
      }

   }

   public void loadConfig() {
      this.plugin.saveDefaultConfig();
      FileConfiguration config = this.plugin.getConfig();
      if (!config.getBoolean("broadcast.enable", true)) {
         this.messages.clear();
         if (this.broadcastTask != null) {
            this.broadcastTask.cancel();
         }

      } else {
         this.messageInterval = config.getInt("broadcast.interval", 240);
         if (this.messageInterval < 10) {
            this.messageInterval = 10;
         }

         this.messages.clear();
         List<String> cfgMessages = config.getStringList("broadcast.messages");
         if (cfgMessages.isEmpty()) {
            this.messages.add(this.colorize("&a\u6b22\u8fce\u6e38\u73a9 &c\u730e\u4eba\u6e38\u620f\uff01"));
         } else {
            for(String msg : cfgMessages) {
               if (!this.shouldSkipMessage(msg)) {
                  this.messages.add(this.colorize(msg));
               }
            }

            if (this.messages.isEmpty()) {
               this.messages.add(this.colorize("&a欢迎游玩 &c终章之战&f！"));
            }
         }

         if (this.broadcastTask != null) {
            this.startBroadcasting();
         }

         this.currentIndex = 0;
      }
   }

   private String colorize(String message) {
      return message == null ? "" : ChatColor.translateAlternateColorCodes('&', message);
   }

   private boolean shouldSkipMessage(String message) {
      if (message == null) {
         return true;
      }

      String plain = ChatColor.stripColor(this.colorize(message));
      return plain != null && (plain.contains("原版猎人") || plain.contains("技能之战") || plain.contains("三大模式") || plain.contains("多个末地传送门") || plain.contains("末影珍珠只会跟随"));
   }

   public boolean isEnabled() {
      return this.plugin.getConfig().getBoolean("broadcast.enable", true);
   }

   public void startBroadcasting() {
      if (this.broadcastTask != null) {
         this.broadcastTask.cancel();
      }

      this.broadcastTask = new BukkitRunnable() {
         public void run() {
            if (MessageBroadcaster.this.messages.isEmpty()) {
               this.cancel();
            } else {
               String message = (String)MessageBroadcaster.this.messages.get(MessageBroadcaster.this.currentIndex);
               Bukkit.broadcastMessage(message);
               MessageBroadcaster.this.currentIndex = (MessageBroadcaster.this.currentIndex + 1) % MessageBroadcaster.this.messages.size();
            }
         }
      };
      this.broadcastTask.runTaskTimer(this.plugin, 0L, (long)this.messageInterval * 20L);
   }

   public void stopBroadcasting() {
      if (this.broadcastTask != null) {
         this.broadcastTask.cancel();
         this.broadcastTask = null;
      }

   }

   public List<String> getMessages() {
      return new ArrayList(this.messages);
   }
}
