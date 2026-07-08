package com.huntergame.world;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class EndermanLimiter implements Listener {
   private final HunterGame plugin;
   private final int endermanLimit;
   private final int checkInterval;
   private BukkitTask checkTask;

   public EndermanLimiter(HunterGame plugin) {
      this.plugin = plugin;
      this.endermanLimit = plugin.getConfig().getInt("game.enderman_limit", 10);
      this.checkInterval = Math.max(100, plugin.getConfig().getInt("game.enderman_check_interval", 200));
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      this.startEndermanCheckTask();
   }

   @EventHandler
   public void onEndermanSpawn(CreatureSpawnEvent event) {
      if (event.getEntity() instanceof Enderman && event.getLocation().getWorld().getEnvironment() == Environment.THE_END) {
         int currentCount = this.countEndermenInEnd(event.getLocation().getWorld());
         if (currentCount >= this.endermanLimit) {
            event.setCancelled(true);
         }
      }

   }

   private void startEndermanCheckTask() {
      this.stop();
      this.checkTask = (new BukkitRunnable() {
         public void run() {
            if (EndermanLimiter.this.plugin.isGameRunning()) {
               for(World world : Bukkit.getWorlds()) {
                  if (world.getEnvironment() == Environment.THE_END) {
                     int currentCount = EndermanLimiter.this.countEndermenInEnd(world);
                     if (currentCount > EndermanLimiter.this.endermanLimit) {
                        EndermanLimiter.this.removeExcessEndermen(world, currentCount - EndermanLimiter.this.endermanLimit);
                     }
                  }
               }

            }
         }
      }).runTaskTimer(this.plugin, 0L, (long)this.checkInterval);
   }

   public void stop() {
      if (this.checkTask != null) {
         this.checkTask.cancel();
         this.checkTask = null;
      }

   }

   private int countEndermenInEnd(World endWorld) {
      return endWorld.getEntitiesByClass(Enderman.class).size();
   }

   private void removeExcessEndermen(World endWorld, int excess) {
      int removed = 0;

      for(Enderman enderman : endWorld.getEntitiesByClass(Enderman.class)) {
         if (removed >= excess) {
            break;
         }

         enderman.remove();
         ++removed;
      }

   }
}
