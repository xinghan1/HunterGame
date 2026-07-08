package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerMoveEvent;

public class WaitingLobbyListener implements Listener {
   private final HunterGame plugin;

   public WaitingLobbyListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onWaitingPlayerMove(PlayerMoveEvent event) {
      if (!this.plugin.isGameRunning() && !this.plugin.isServerClosing()) {
         Location to = event.getTo();
         if (to != null && to.getWorld() != null) {
            if (to.getY() <= (double)to.getWorld().getMinHeight()) {
               this.teleportWaitingPlayerToLobby(event.getPlayer());
            }

         }
      }
   }

   @EventHandler
   public void onWaitingVoidDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player && !this.plugin.isGameRunning() && !this.plugin.isServerClosing()) {
         if (event.getCause() == DamageCause.VOID) {
            event.setCancelled(true);
            this.teleportWaitingPlayerToLobby((Player)event.getEntity());
         }
      }
   }

   private void teleportWaitingPlayerToLobby(Player player) {
      Location lobbyLocation = this.plugin.getLobbySpawnLocation();
      if (lobbyLocation == null) {
         player.sendMessage(this.plugin.getMessage("lobby_not_set", "&c\u5927\u5385\u4f4d\u7f6e\u672a\u6b63\u786e\u8bbe\u7f6e\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\uff01"));
      } else {
         player.setFallDistance(0.0F);
         player.teleport(lobbyLocation);
      }
   }
}
