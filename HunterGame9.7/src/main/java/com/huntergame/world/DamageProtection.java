package com.huntergame.world;

import com.huntergame.HunterGame;
import java.util.UUID;
import org.bukkit.World.Environment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

public class DamageProtection implements Listener {
   private final HunterGame plugin;

   public DamageProtection(HunterGame plugin) {
      this.plugin = plugin;
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
   }

   @EventHandler
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (!this.plugin.isFinalBattleMode()) {
         Entity victim = event.getEntity();
         if (victim instanceof Player) {
            Player player = (Player)victim;
            UUID playerId = player.getUniqueId();
            Entity attacker = event.getDamager();
            if (!(attacker instanceof Player)) {
               double originalDamage = event.getDamage();
               double finalDamage = this.applyGeneralDamageReduction(playerId, originalDamage);
               event.setDamage(finalDamage);
            }
         }
      }
   }

   @EventHandler
   public void onEntityDamage(EntityDamageEvent event) {
      if (!this.plugin.isFinalBattleMode()) {
         if (event.getEntity() instanceof Player) {
            Player player = (Player)event.getEntity();
            UUID playerId = player.getUniqueId();
            double originalDamage = event.getDamage();
            double finalDamage = this.applyGeneralDamageReduction(playerId, originalDamage);
            if (event.getCause() == DamageCause.FALL && player.getWorld().getEnvironment() == Environment.THE_END) {
               finalDamage *= 0.7;
            }

            event.setDamage(finalDamage);
         }
      }
   }

   private double applyGeneralDamageReduction(UUID playerId, double originalDamage) {
      return this.plugin.isEscaper(playerId) ? originalDamage * (double)0.75F : originalDamage * (double)0.75F;
   }
}
