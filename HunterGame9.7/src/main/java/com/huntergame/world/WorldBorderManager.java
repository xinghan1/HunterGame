package com.huntergame.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;

public class WorldBorderManager {
   public static void setupWorldBorder() {
      World world = Bukkit.getWorld("world");
      if (world == null) {
         Bukkit.getLogger().warning("\u627e\u4e0d\u5230\u540d\u4e3a 'world' \u7684\u4e16\u754c\uff01");
      } else {
         WorldBorder border = world.getWorldBorder();
         border.setCenter((double)0.0F, (double)0.0F);
         border.setSize((double)10000.0F);
         border.setDamageAmount((double)2.0F);
         border.setWarningDistance(0);
      }
   }

   public static void setupEndWorldBorder() {
      setupEndWorldBorder("world_the_end", 0, 90, 0, 300.0D);
   }

   public static void setupEndWorldBorder(String worldName, int centerX, int spawnY, int centerZ, double borderSize) {
      World endWorld = Bukkit.getWorld(worldName);
      if (endWorld == null) {
         endWorld = Bukkit.createWorld(new WorldCreator(worldName).environment(Environment.THE_END));
      }

      if (endWorld == null) {
         Bukkit.getLogger().warning("找不到也无法加载末地世界: " + worldName);
         return;
      }

      endWorld.setSpawnLocation(centerX, spawnY, centerZ);
      WorldBorder border = endWorld.getWorldBorder();
      border.setCenter((double)centerX, (double)centerZ);
      border.setSize(borderSize);
      border.setDamageAmount((double)2.0F);
      border.setWarningDistance(0);
      Bukkit.getLogger().info("末地世界边界已设置: world=" + worldName + ", spawn=(" + centerX + ", " + spawnY + ", " + centerZ + "), borderCenter=(" + centerX + ", " + centerZ + "), size=" + borderSize);
   }
}
