package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.game.EscaperQuitCountdown;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.potion.PotionEffect;

public class PlayerConnectionListener implements Listener {
   private final HunterGame plugin;
   private final EscaperQuitCountdown escaperQuitCountdown;
   private final ServerSelectorListener serverSelectorListener;

   public PlayerConnectionListener(HunterGame plugin, EscaperQuitCountdown escaperQuitCountdown, ServerSelectorListener serverSelectorListener) {
      this.plugin = plugin;
      this.escaperQuitCountdown = escaperQuitCountdown;
      this.serverSelectorListener = serverSelectorListener;
   }

   @EventHandler
   public void onPlayerLogin(PlayerLoginEvent event) {
      if (this.plugin.isResetting()) {
         event.disallow(Result.KICK_OTHER, this.plugin.getMessage("login_resetting_kick", "&c\u670d\u52a1\u5668\u6b63\u5728\u91cd\u7f6e\u4e2d\uff0c\u8bf7\u7a0d\u540e\u518d\u52a0\u5165\uff01"));
      } else {
         if (this.plugin.isServerClosing()) {
            event.disallow(Result.KICK_OTHER, this.plugin.getMessage("login_server_closing_kick", "&c\u6e38\u620f\u5df2\u7ed3\u675f\uff0c\u6b63\u5728\u51c6\u5907\u91cd\u7f6e\uff01"));
         }

      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      Location lobbyLocation = this.plugin.getLobbySpawnLocation();
      if (this.escaperQuitCountdown.isScheduled() && this.plugin.isEscaper(playerId)) {
         this.escaperQuitCountdown.cancel();
      }

      if (!this.plugin.isGameRunning()) {
         this.resetWaitingPlayer(player);
         if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(this.plugin.getMessage("welcome_message", "&a\u6b22\u8fce\u6765\u5230\u730e\u4eba\u6e38\u620f\uff01"));
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               player.getInventory().clear();
               this.serverSelectorListener.giveServerSelector(player);
               this.plugin.getGuideManager().giveTriggerItem(player);
            }, 5L);
         } else {
            player.sendMessage(this.plugin.getMessage("lobby_not_set", "&c\u5927\u5385\u4f4d\u7f6e\u672a\u6b63\u786e\u8bbe\u7f6e\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\uff01"));
         }

         event.setJoinMessage(this.plugin.getMessage("player_join", "&a&l[+] &f%player% (&e%online%&a/%max%)").replace("%player%", player.getName()).replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size())).replace("%max%", String.valueOf(Bukkit.getMaxPlayers())));
      } else {
         if (this.plugin.isRealSpectator(playerId) || this.plugin.isDeathescapers(playerId)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SPECTATOR);
            this.plugin.addRealSpectator(playerId);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.teleportSpectatorToRandomPlayer(player), 20L);
         } else {
            player.setAllowFlight(false);
            player.setFlying(false);
         }

         event.setJoinMessage(this.plugin.getMessage("player_join_start", "&a&l[+] &e%player%").replace("%player%", player.getName()));
         if (this.plugin.isEscaper(playerId)) {
            this.plugin.getHunterTracker().startTrackingEscaper(player);
         }

      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (!this.plugin.isGameRunning()) {
         this.plugin.removeRealSpectator(playerId);
      }

      if (this.plugin.getStartGameCommand() != null && this.plugin.getStartGameCommand().getVoteSystem() != null) {
         this.plugin.getStartGameCommand().getVoteSystem().clearPlayerVote(playerId);
      }

      if (this.plugin.isEscaper(playerId) && this.plugin.getEscapers().size() == 1) {
         this.escaperQuitCountdown.start();
      }

      if (this.plugin.isEscaper(playerId)) {
         this.plugin.removeEscaper(playerId);
      }

      if (this.plugin.isHunter(playerId)) {
         this.plugin.removeHunter(playerId);
      }

      if (!this.plugin.isGameRunning()) {
         event.setQuitMessage(this.plugin.getMessage("player_quit", "&c&l[-] &e%player% (&e%online%&c/%max%)").replace("%player%", player.getName()).replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size() - 1)).replace("%max%", String.valueOf(Bukkit.getMaxPlayers())));
      } else {
         event.setQuitMessage(this.plugin.getMessage("player_quit_start", "&c&l[-] &e%player%").replace("%player%", player.getName()));
      }
   }

   private void resetWaitingPlayer(Player player) {
      for(PotionEffect effect : player.getActivePotionEffects()) {
         player.removePotionEffect(effect.getType());
      }

      AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
      if (maxHealth != null) {
         maxHealth.setBaseValue((double)20.0F);
      }

      player.setAllowFlight(false);
      player.setFlying(false);
   }
}
