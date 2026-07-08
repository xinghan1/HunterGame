package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.combat.LastDamageTracker;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class GameDeathListener implements Listener {
   private final HunterGame plugin;

   public GameDeathListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      UUID playerId = player.getUniqueId();
      LastDamageTracker.DamageCredit killer = this.plugin.getLastDamageTracker().getCreditedKiller(player);
      if (!this.plugin.isGameRunning()) {
         Location lobbyLocation = this.plugin.getLobbyLocation();
         if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
         }

      } else {
         if (killer != null) {
            this.plugin.getDataStorageManager().addKill(killer.playerId());
            this.plugin.getDataStorageManager().addKillput(killer.playerId(), killer.playerName());
         }

         this.plugin.getDataStorageManager().addDeath(playerId, player);
         if (this.plugin.isHunter(playerId)) {
            this.handleHunterDeath(player, playerId);
         } else {
            if (this.plugin.isEscaper(playerId)) {
               this.handleEscaperDeath(player, playerId);
            }

         }
      }
   }

   private void handleHunterDeath(Player player, UUID playerId) {
      if (this.plugin.isFinalBattleMode()) {
         this.plugin.removeHunter(playerId);
         this.plugin.addRealSpectator(playerId);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
               player.setAllowFlight(false);
               player.setFlying(false);
               player.setGameMode(GameMode.SPECTATOR);
               this.plugin.teleportSpectatorToRandomPlayer(player);
            }
         }, 2L);
         if (!this.plugin.getStartGameCommand().isPlayerRespawning(playerId)) {
            boolean rewarded = this.plugin.getGameRewardService().giveHunterFailReward(player);
            if (rewarded) {
               player.sendTitle(this.plugin.getMessage("hunters_defeat_title", "&c\u4f60\u5931\u8d25\u4e86\uff01"), this.plugin.getMessage("hunters_defeat_subtitle", "&f\u7ec8\u7ae0\u6a21\u5f0f\u6b7b\u4ea1\u540e\u65e0\u6cd5\u590d\u6d3b"), 10, 80, 20);
            }
         }
      }
   }

   private void handleEscaperDeath(Player player, UUID playerId) {
      this.plugin.removeEscaper(playerId);
      this.plugin.addDeathescapers(playerId);
      this.plugin.addRealSpectator(playerId);
      player.setAllowFlight(false);
      player.setFlying(false);
      player.setGameMode(GameMode.SPECTATOR);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SPECTATOR);
            this.plugin.teleportSpectatorToRandomPlayer(player);
         }
      }, 2L);
      if (!this.plugin.isFinalBattleMode()) {
         player.sendMessage(this.plugin.getMessage("death_to_spectator", "&7\u4f60\u5df2\u7ecf\u6b7b\u4ea1\uff0c\u73b0\u5728\u53d8\u6210\u4e86\u65c1\u89c2\u8005\u3002"));
      }

      boolean rewarded = this.plugin.getGameRewardService().giveEscaperFailReward(player);
      if (rewarded) {
         player.sendTitle(this.plugin.getMessage("escapers_defeat_title", "&c\u4f60\u5931\u8d25\u4e86\uff01"), this.plugin.getMessage("escapers_defeat_subtitle", "&f\u4f60\u5df2\u63d0\u524d\u7ed3\u7b97\uff0c\u6e38\u620f\u4ecd\u4f1a\u7ee7\u7eed"), 10, 80, 20);
      }

      if (this.plugin.getEscapers().isEmpty()) {
         this.endGameWithHuntersWin();
      } else {
         Bukkit.broadcastMessage(this.plugin.getMessage("remaining_escapers", "&b\u9003\u751f\u8005\u8fd8\u5269: %escapers% \u4eba").replace("%escapers%", String.valueOf(this.plugin.getEscapers().size())));
      }

   }

   private void endGameWithHuntersWin() {
      if (this.plugin.beginSettlement()) {
         Bukkit.broadcastMessage(this.plugin.getMessage("hunters_win", "&c\u6240\u6709\u9003\u751f\u8005\u5df2\u6b7b\u4ea1\uff01\u730e\u4eba\u80dc\u5229\uff01"));

         for(Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID onlinePlayerId = onlinePlayer.getUniqueId();
            if (!this.plugin.getGameRewardService().hasSettlementReward(onlinePlayerId)) {
               this.plugin.getDataStorageManager().saveTotalWins(onlinePlayerId, onlinePlayer);
               if (this.plugin.isHunter(onlinePlayerId)) {
                  this.plugin.getDataStorageManager().addHunterWin(onlinePlayerId, onlinePlayer);
                  this.plugin.getGameRewardService().giveHunterReward(onlinePlayer);
                  onlinePlayer.sendTitle(this.plugin.getMessage("hunters_victory_title", "&a\u606d\u559c\u4f60\uff01"), this.plugin.getMessage("hunters_victory_subtitle", "&f\u6210\u529f\u8ffd\u6740\u6240\u6709\u9003\u751f\u8005"), 10, 100, 20);
               } else {
                  onlinePlayer.sendTitle(this.plugin.getMessage("game_over_title", "&6\u6e38\u620f\u7ed3\u675f\uff01"), this.plugin.getMessage("game_over_subtitle", "&c\u730e\u4eba\u83b7\u5f97\u4e86\u80dc\u5229\uff01"), 10, 100, 20);
               }
            }
         }

         this.plugin.resetGame();
      }
   }
}
