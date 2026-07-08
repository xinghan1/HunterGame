package com.huntergame.spectator;

import com.huntergame.HunterGame;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class SpectatorService {
   private static final Particle.DustOptions DIRECTION_PARTICLE;
   private final HunterGame plugin;
   private final Random random = new Random();

   public SpectatorService(HunterGame plugin) {
      this.plugin = plugin;
   }

   public void showHunterParticleDirection(Player hunter) {
      Player nearestEscaper = this.findNearestEscaper(hunter);
      if (nearestEscaper != null) {
         Location hunterLocation = hunter.getLocation();
         Location escaperLocation = nearestEscaper.getLocation();
         Vector direction = escaperLocation.toVector().subtract(hunterLocation.toVector()).normalize();
         Location particleLocation = hunterLocation.clone().add((double)0.0F, 0.1, (double)0.0F);

         for(double distance = (double)0.0F; distance <= (double)2.0F; distance += 0.2) {
            Location point = particleLocation.clone().add(direction.clone().multiply(distance));
            hunter.getWorld().spawnParticle(Particle.REDSTONE, point, 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, DIRECTION_PARTICLE);
         }

      }
   }

   public void checkAndTeleportSpectators() {
      int maxDistance = this.plugin.getConfig().getInt("game.spectator_max_distance", 100);

      for(Player player : Bukkit.getOnlinePlayers()) {
         if (player.getGameMode() == GameMode.SPECTATOR && this.plugin.isRealSpectator(player.getUniqueId())) {
            Player spectatorTarget = this.getSpectatorTarget(player);
            if (spectatorTarget == null || !spectatorTarget.isOnline()) {
               this.teleportBackIfTooFar(player, maxDistance);
            }
         }
      }

   }

   public void teleportToRandomPlayer(Player spectator) {
      List<Player> availablePlayers = new ArrayList();
      this.addActivePlayers(availablePlayers, this.plugin.getHunters());
      this.addActivePlayers(availablePlayers, this.plugin.getEscapers());
      if (!availablePlayers.isEmpty()) {
         Player target = (Player)availablePlayers.get(this.random.nextInt(availablePlayers.size()));
         spectator.teleport(target.getLocation());
      }

   }

   private Player findNearestEscaper(Player hunter) {
      Player nearestEscaper = null;
      double minDistance = Double.MAX_VALUE;

      for(Player escaper : this.plugin.getEscapers()) {
         if (this.isActiveInSameWorld(escaper, hunter)) {
            double distance = hunter.getLocation().distance(escaper.getLocation());
            if (distance < minDistance) {
               minDistance = distance;
               nearestEscaper = escaper;
            }
         }
      }

      return nearestEscaper;
   }

   private void teleportBackIfTooFar(Player spectator, int maxDistance) {
      Player nearestPlayer = this.findNearestPlayer(spectator);
      if (nearestPlayer != null) {
         if (!spectator.getWorld().equals(nearestPlayer.getWorld())) {
            this.teleportNearPlayer(spectator, nearestPlayer);
         } else {
            try {
               double distance = spectator.getLocation().distance(nearestPlayer.getLocation());
               if (distance > (double)maxDistance) {
                  this.teleportNearPlayer(spectator, nearestPlayer);
               }
            } catch (IllegalArgumentException var6) {
               this.teleportNearPlayer(spectator, nearestPlayer);
            }

         }
      }
   }

   private Player findNearestPlayer(Player spectator) {
      Player nearest = null;
      double minDistance = Double.MAX_VALUE;

      for(Player target : Bukkit.getOnlinePlayers()) {
         if (this.isTeleportAnchor(spectator, target) && target.getWorld().equals(spectator.getWorld())) {
            try {
               double distance = spectator.getLocation().distance(target.getLocation());
               if (distance < minDistance) {
                  minDistance = distance;
                  nearest = target;
               }
            } catch (IllegalArgumentException var9) {
            }
         }
      }

      if (nearest != null) {
         return nearest;
      } else {
         for(Player target : Bukkit.getOnlinePlayers()) {
            if (this.isTeleportAnchor(spectator, target)) {
               return target;
            }
         }

         return null;
      }
   }

   private void teleportNearPlayer(Player spectator, Player target) {
      spectator.teleport(target.getLocation());
      spectator.sendMessage(this.plugin.getMessage("spectator_stay_near_target", "&7\u4e0d\u53ef\u4ee5\u8dd1\u8fdc\u4e86\uff0c\u5df2\u4f20\u9001\u5230 %player% \u9644\u8fd1").replace("%player%", target.getName()));
   }

   private Player getSpectatorTarget(Player spectator) {
      Entity target = spectator.getSpectatorTarget();
      return target instanceof Player ? (Player)target : null;
   }

   private void addActivePlayers(List<Player> availablePlayers, List<Player> players) {
      for(Player player : players) {
         if (player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
            availablePlayers.add(player);
         }
      }

   }

   private boolean isActiveInSameWorld(Player player, Player reference) {
      return player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR && player.getWorld().equals(reference.getWorld());
   }

   private boolean isTeleportAnchor(Player spectator, Player target) {
      return !target.equals(spectator) && target.getGameMode() != GameMode.SPECTATOR;
   }

   static {
      DIRECTION_PARTICLE = new Particle.DustOptions(Color.BLUE, 1.0F);
   }
}
