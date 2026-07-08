package com.huntergame.util;

import com.huntergame.HunterGame;
import java.util.Random;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SafeLocationFinder {
   private final HunterGame plugin;
   private final Random random = new Random();

   public SafeLocationFinder(HunterGame plugin) {
      this.plugin = plugin;
   }

   public void findLocation(final World world, final Location center, final double minRadius, final double maxRadius, final Consumer<Location> callback) {
      (new BukkitRunnable() {
         int secondsPassed = 0;
         int ticksElapsed = 0;
         double currentMinRadius = minRadius;
         double radiusStep = (maxRadius - minRadius) / (double)5.0F;
         final int CHECKS_PER_TICK = 3;
         final int MAX_SECONDS = 60;

         public void run() {
            if (this.secondsPassed >= 60) {
               Location defaultLoc = center.clone();
               if (world != null) {
                  int highestY = world.getHighestBlockYAt(defaultLoc);
                  defaultLoc.setY((double)(highestY + 1));
               }

               Bukkit.broadcastMessage(SafeLocationFinder.this.plugin.getMessage("location_timeout", "&c\u641c\u5bfb\u8d85\u65f6\uff0c\u4f7f\u7528\u9ed8\u8ba4\u5750\u6807\uff01"));
               callback.accept(defaultLoc);
               this.cancel();
            } else {
               if (this.ticksElapsed % 20 == 0) {
                  String title = SafeLocationFinder.this.plugin.getMessage("finding_location_title", "&e\u6b63\u5728\u5bfb\u627e\u5b89\u5168\u4f4d\u7f6e...");
                  String subtitle = SafeLocationFinder.this.plugin.getMessage("finding_location_subtitle", "&f\u5269\u4f59\u65f6\u95f4: %time%s").replace("%time%", String.valueOf(60 - this.secondsPassed));

                  for(Player p : Bukkit.getOnlinePlayers()) {
                     p.sendTitle(title, subtitle, 0, 25, 5);
                  }

                  ++this.secondsPassed;
               }

               for(int i = 0; i < 3; ++i) {
                  if (this.currentMinRadius > maxRadius) {
                     this.currentMinRadius = minRadius;
                  }

                  double currentMaxRadius = Math.min(this.currentMinRadius + this.radiusStep, maxRadius);
                  double angle = SafeLocationFinder.this.random.nextDouble() * (double)2.0F * Math.PI;
                  double radius = this.currentMinRadius + SafeLocationFinder.this.random.nextDouble() * (currentMaxRadius - this.currentMinRadius);
                  int x = center.getBlockX() + (int)(Math.cos(angle) * radius);
                  int z = center.getBlockZ() + (int)(Math.sin(angle) * radius);
                  if (SafeLocationFinder.this.isValidCoordinate(x, z)) {
                     int y = world.getHighestBlockYAt(x, z);
                     Location loc = new Location(world, (double)x, (double)y, (double)z);
                     if (SafeLocationFinder.this.isSafeLocation(loc)) {
                        callback.accept(loc.add((double)0.0F, (double)1.0F, (double)0.0F));
                        this.cancel();
                        return;
                     }
                  }
               }

               ++this.ticksElapsed;
            }
         }
      }).runTaskTimer(this.plugin, 0L, 1L);
   }

   public Location getFallbackLocation(World world, Location center, double minDistance) {
      double angle = this.random.nextDouble() * (double)2.0F * Math.PI;
      double distance = minDistance + this.random.nextDouble() * (double)20.0F;
      double x = center.getX() + distance * Math.cos(angle);
      double z = center.getZ() + distance * Math.sin(angle);
      int highestY = world.getHighestBlockYAt((int)x, (int)z);
      return new Location(world, x, (double)(highestY + 1), z);
   }

   private boolean isValidCoordinate(int x, int z) {
      int borderSize = 30000000;
      return x >= -borderSize && x <= borderSize && z >= -borderSize && z <= borderSize;
   }

   private boolean isSafeLocation(Location location) {
      World world = location.getWorld();
      int x = location.getBlockX();
      int y = location.getBlockY();
      int z = location.getBlockZ();
      Block below = world.getBlockAt(x, y - 1, z);
      Block block = world.getBlockAt(x, y, z);
      Block above = world.getBlockAt(x, y + 1, z);
      return below.getType().isSolid() && !block.isLiquid() && !above.isLiquid() && y > 0 && !this.isBadlandsBiome(world.getBiome(x, y, z));
   }

   private boolean isBadlandsBiome(Biome biome) {
      return biome.equals(Biome.BADLANDS) || biome.equals(Biome.WOODED_BADLANDS) || biome.equals(Biome.ERODED_BADLANDS) || biome.equals(Biome.FROZEN_OCEAN) || biome.equals(Biome.DEEP_COLD_OCEAN) || biome.equals(Biome.LUKEWARM_OCEAN) || biome.equals(Biome.DEEP_LUKEWARM_OCEAN) || biome.equals(Biome.WARM_OCEAN) || biome.equals(Biome.COLD_OCEAN) || biome.equals(Biome.BEACH) || biome.equals(Biome.SNOWY_BEACH) || biome.equals(Biome.OCEAN) || biome.equals(Biome.SNOWY_SLOPES) || biome.equals(Biome.SNOWY_TAIGA) || biome.equals(Biome.DESERT);
   }
}
