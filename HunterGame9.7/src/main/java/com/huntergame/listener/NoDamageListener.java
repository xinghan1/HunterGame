package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class NoDamageListener implements Listener {
   private final HunterGame plugin;

   public NoDamageListener(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onPlayerDamage(EntityDamageEvent event) {
      if (!this.plugin.isGameRunning()) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      if (!this.plugin.isGameRunning()) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onBlockPlace(BlockPlaceEvent event) {
      if (!this.plugin.isGameRunning()) {
         event.setCancelled(true);
      }

   }
}
