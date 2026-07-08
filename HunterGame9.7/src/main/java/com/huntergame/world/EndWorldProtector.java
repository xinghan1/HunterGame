package com.huntergame.world;

import com.huntergame.HunterGame;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

public class EndWorldProtector {
   private final HunterGame plugin;
   private BukkitRunnable protectionTask;
   private final Set<UUID> protectedWorlds = new HashSet();
   private final int PROTECT_X = 0;
   private final int PROTECT_Y = 70;
   private final int PROTECT_Z = 0;
   private final int PROTECT_RADIUS = 1;

   public EndWorldProtector(HunterGame plugin) {
      this.plugin = plugin;
      this.startProtectionTask();
   }

   private void startProtectionTask() {
      this.stopProtectionTask();
      this.protectionTask = new BukkitRunnable() {
         public void run() {
            if (EndWorldProtector.this.plugin.isGameRunning()) {
               World endWorld = Bukkit.getWorld("world_the_end");
               if (endWorld != null) {
                  EndWorldProtector.this.protectEndSpawn(endWorld);
               }
            }
         }
      };
      this.protectionTask.runTaskTimer(this.plugin, 0L, 100L);
   }

   public void stopProtectionTask() {
      if (this.protectionTask != null) {
         this.protectionTask.cancel();
         this.protectionTask = null;
      }

   }

   private void protectEndSpawn(World world) {
      this.protectedWorlds.add(world.getUID());

      for(int x = -1; x <= 1; ++x) {
         for(int y = 69; y <= 71; ++y) {
            for(int z = -1; z <= 1; ++z) {
               Block block = world.getBlockAt(x, y, z);
               if (block.getType() != Material.AIR) {
                  block.setType(Material.AIR);
               }
            }
         }
      }

   }

   public void cleanup() {
      this.stopProtectionTask();
      this.protectedWorlds.clear();
   }
}
