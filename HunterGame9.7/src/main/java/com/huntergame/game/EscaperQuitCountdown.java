package com.huntergame.game;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class EscaperQuitCountdown {
   private final HunterGame plugin;
   private int taskId = -1;
   private int secondsLeft;
   private boolean scheduled;

   public EscaperQuitCountdown(HunterGame plugin) {
      this.plugin = plugin;
   }

   public boolean isScheduled() {
      return this.scheduled;
   }

   public void start() {
      if (!this.scheduled) {
         this.scheduled = true;
         FileConfiguration config = this.plugin.getConfig();
         this.secondsLeft = config.getInt("game.escapers_quit_countdown", 120);
         Bukkit.broadcastMessage(this.plugin.getMessage("escaper_quit_countdown_started", "&c\u6240\u6709\u9003\u751f\u8005\u5df2\u9000\u51fa\uff0c%seconds%\u79d2\u540e\u730e\u4eba\u80dc\u5229\uff01").replace("%seconds%", String.valueOf(this.secondsLeft)));
         this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
            --this.secondsLeft;
            if (this.secondsLeft <= 0) {
               Bukkit.getScheduler().cancelTask(this.taskId);
               this.taskId = -1;
               this.scheduled = false;
               if (this.plugin.beginSettlement()) {
                  for(Player hunter : this.plugin.getHunters()) {
                     if (!this.plugin.getGameRewardService().hasSettlementReward(hunter.getUniqueId())) {
                        this.plugin.getDataStorageManager().addHunterWin(hunter.getUniqueId(), hunter);
                        this.plugin.getGameRewardService().giveHunterReward(hunter);
                     }
                  }

                  for(Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                     if (!this.plugin.getGameRewardService().hasSettlementReward(onlinePlayer.getUniqueId())) {
                        this.plugin.getDataStorageManager().saveTotalWins(onlinePlayer.getUniqueId(), onlinePlayer);
                        onlinePlayer.sendTitle(this.plugin.getMessage("hunters_victory_title", "&6\u606d\u559c\uff01"), this.plugin.getMessage("hunters_victory_subtitle", "&c\u730e\u4eba\u80dc\u5229\uff01"), 10, 70, 20);
                     }
                  }

                  this.plugin.resetGame();
               }
            } else {
               if (this.secondsLeft % 5 == 0 || this.secondsLeft <= 5) {
                  Bukkit.broadcastMessage(this.plugin.getMessage("escaper_quit_countdown_remaining", "&e\u5269\u4f59\u65f6\u95f4: %seconds%\u79d2").replace("%seconds%", String.valueOf(this.secondsLeft)));
               }

            }
         }, 20L, 20L);
      }
   }

   public void cancel() {
      this.cancelTask();
      this.scheduled = false;
      Bukkit.broadcastMessage(this.plugin.getMessage("escaper_quit_countdown_cancelled", "&a\u9003\u751f\u8005\u5df2\u56de\u5f52\uff0c\u53d6\u6d88\u5012\u8ba1\u65f6\uff01"));
   }

   public void cancelSilently() {
      this.cancelTask();
      this.scheduled = false;
   }

   private void cancelTask() {
      if (this.taskId != -1) {
         Bukkit.getScheduler().cancelTask(this.taskId);
         this.taskId = -1;
      }

   }
}
