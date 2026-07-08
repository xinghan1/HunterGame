package com.huntergame.portal;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class EndPortalTracker {
   private static final int BLOCKS_PER_TICK = 900;
   private final HunterGame plugin;
   private boolean speedGranted = false;
   private boolean searchRunning = false;
   private Location portalLocation;
   private BukkitTask searchTask;

   public EndPortalTracker(HunterGame plugin) {
      this.plugin = plugin;
   }

   public Location findAndSetNearestEndPortal(Player player, int radius) {
      if (this.portalLocation != null) {
         return this.portalLocation;
      } else if (this.searchRunning) {
         return null;
      } else {
         Location center = player.getLocation().clone();
         if (center.getWorld() == null) {
            return null;
         } else {
            this.startSearch(center, radius);
            return null;
         }
      }
   }

   public String getPortalCoordinatesPlaceholder(Player player) {
      if (this.portalLocation != null) {
         return this.formatBlockLocation(this.portalLocation);
      } else {
         this.findAndSetNearestEndPortal(player, 20);
         return this.searchRunning ? "\u641c\u7d22\u4e2d" : "\u672a\u627e\u5230";
      }
   }

   public void reset() {
      this.portalLocation = null;
      this.speedGranted = false;
      this.cancelSearch();
   }

   public void cancelSearch() {
      this.searchRunning = false;
      if (this.searchTask != null) {
         this.searchTask.cancel();
         this.searchTask = null;
      }

   }

   private void startSearch(final Location center, int radius) {
      this.searchRunning = true;
      final World world = center.getWorld();
      final int minX = center.getBlockX() - radius;
      final int maxX = center.getBlockX() + radius;
      final int minY = Math.max(world.getMinHeight(), center.getBlockY() - radius);
      final int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + radius);
      final int minZ = center.getBlockZ() - radius;
      final int maxZ = center.getBlockZ() + radius;
      this.searchTask = (new BukkitRunnable() {
         private int x = minX;
         private int y = minY;
         private int z = minZ;
         private Location nearest;
         private double nearestDistanceSquared = Double.MAX_VALUE;

         public void run() {
            for(int checked = 0; this.x <= maxX && checked < 900; ++checked) {
               Location location = new Location(world, (double)this.x, (double)this.y, (double)this.z);
               if (location.getBlock().getType() == Material.END_PORTAL) {
                  double distanceSquared = center.distanceSquared(location);
                  if (distanceSquared < this.nearestDistanceSquared) {
                     this.nearestDistanceSquared = distanceSquared;
                     this.nearest = location;
                  }
               }

               this.advanceCursor();
            }

            if (this.x > maxX) {
               EndPortalTracker.this.finishSearch(this.nearest);
               this.cancel();
            }

         }

         private void advanceCursor() {
            ++this.z;
            if (this.z > maxZ) {
               this.z = minZ;
               ++this.y;
               if (this.y > maxY) {
                  this.y = minY;
                  ++this.x;
               }
            }
         }
      }).runTaskTimer(this.plugin, 0L, 1L);
   }

   private void finishSearch(Location nearest) {
      if (nearest != null) {
         this.portalLocation = nearest;
         this.grantHuntersSpeed();
      }

      this.searchRunning = false;
      this.searchTask = null;
   }

   private void grantHuntersSpeed() {
      if (!this.speedGranted) {
         for(Player player : Bukkit.getOnlinePlayers()) {
            if (this.plugin.isHunter(player.getUniqueId())) {
               player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 1));
            }
         }

         this.speedGranted = true;
      }
   }

   private String formatBlockLocation(Location location) {
      int var10000 = location.getBlockX();
      return var10000 + "," + location.getBlockY() + "," + location.getBlockZ();
   }
}
