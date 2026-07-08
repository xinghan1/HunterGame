package com.huntergame.game;

import com.huntergame.HunterGame;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class CageManager {
   private final HunterGame plugin;
   private final Map<UUID, Set<Location>> playerCages = new ConcurrentHashMap();
   private final Set<Location> barrierBlocks = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Integer> taskIds = new ConcurrentHashMap();
   private final Set<Integer> groupTaskIds = ConcurrentHashMap.newKeySet();

   public CageManager(HunterGame plugin) {
      this.plugin = plugin;
   }

   public boolean isInCage(Player player) {
      return this.playerCages.containsKey(player.getUniqueId());
   }

   public void createGroupCage(final List<Player> players, Location center) {
      if (players != null && !players.isEmpty()) {
         final Set<Location> cageBlocks = new HashSet();
         int radius = 2;

         for(int x = -radius; x <= radius; ++x) {
            for(int y = -radius; y <= radius; ++y) {
               for(int z = -radius; z <= radius; ++z) {
                  boolean isSurface = Math.abs(x) == radius || Math.abs(y) == radius || Math.abs(z) == radius;
                  Location loc = center.clone().add((double)x, (double)y, (double)z);
                  Block block = loc.getBlock();
                  if (isSurface) {
                     block.setType(Material.BARRIER);
                     cageBlocks.add(loc);
                     this.barrierBlocks.add(loc);
                  } else {
                     block.setType(Material.AIR);
                  }
               }
            }
         }

         for(Player player : players) {
            this.playerCages.put(player.getUniqueId(), cageBlocks);
         }

         int delayTicks = this.plugin.getConfig().getInt("hunter_removeCage", 25) * 20;
         BukkitTask task = (new BukkitRunnable() {
            public void run() {
               CageManager.this.groupTaskIds.remove(this.getTaskId());
               cageBlocks.forEach((loc) -> {
                  Block block = loc.getBlock();
                  if (block.getType() == Material.BARRIER) {
                     block.setType(Material.AIR);
                  }

                  CageManager.this.barrierBlocks.remove(loc);
               });

               for(Player player : players) {
                  CageManager.this.playerCages.remove(player.getUniqueId());
               }

               for(Player online : Bukkit.getOnlinePlayers()) {
                  online.sendTitle(CageManager.this.plugin.getMessage("cage_start_title", "&a\u5f00\u59cb\uff01"), CageManager.this.plugin.getMessage("cage_start_subtitle", ""), 5, 40, 15);
               }

            }
         }).runTaskLater(this.plugin, (long)delayTicks);
         this.groupTaskIds.add(task.getTaskId());
      }
   }

   public void cleanup() {
      for(Integer taskId : this.taskIds.values()) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

      this.taskIds.clear();

      for(Integer taskId : this.groupTaskIds) {
         Bukkit.getScheduler().cancelTask(taskId);
      }

      this.groupTaskIds.clear();

      for(Location loc : new HashSet<Location>(this.barrierBlocks)) {
         Block block = loc.getBlock();
         if (block.getType() == Material.BARRIER) {
            block.setType(Material.AIR);
         }
      }

      this.barrierBlocks.clear();
      this.playerCages.clear();
   }
}
