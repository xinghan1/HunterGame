package com.huntergame.combat;

import com.huntergame.HunterGame;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class LastDamageTracker implements Listener {
   private static final long CREDIT_WINDOW_MILLIS = 30000L;
   private final HunterGame plugin;
   private final Map<UUID, DamageCredit> lastPlayerDamage = new HashMap();

   public LastDamageTracker(HunterGame plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPlayerDamaged(EntityDamageByEntityEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof Player victim) {
         if (!(event.getFinalDamage() <= (double)0.0F)) {
            Player damager = this.getPlayerDamager(event.getDamager());
            if (damager != null && !damager.getUniqueId().equals(victim.getUniqueId())) {
               this.lastPlayerDamage.put(victim.getUniqueId(), LastDamageTracker.DamageCredit.of(damager));
               return;
            }

            return;
         }
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerDeath(PlayerDeathEvent event) {
      UUID victimId = event.getEntity().getUniqueId();
      Bukkit.getScheduler().runTask(this.plugin, () -> this.lastPlayerDamage.remove(victimId));
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.lastPlayerDamage.remove(event.getPlayer().getUniqueId());
   }

   public DamageCredit getCreditedKiller(Player victim) {
      DamageCredit directCredit = this.getDirectDamageCredit(victim);
      if (directCredit != null) {
         return directCredit;
      } else {
         DamageCredit credit = (DamageCredit)this.lastPlayerDamage.get(victim.getUniqueId());
         if (credit != null && !credit.playerId().equals(victim.getUniqueId())) {
            return System.currentTimeMillis() - credit.damageTimeMillis() > 30000L ? null : credit;
         } else {
            return null;
         }
      }
   }

   private DamageCredit getDirectDamageCredit(Player victim) {
      EntityDamageEvent var3 = victim.getLastDamageCause();
      if (var3 instanceof EntityDamageByEntityEvent damageEvent) {
         Player damager = this.getPlayerDamager(damageEvent.getDamager());
         return damager != null && !damager.getUniqueId().equals(victim.getUniqueId()) ? LastDamageTracker.DamageCredit.of(damager) : null;
      } else {
         return null;
      }
   }

   public Player getPlayerDamager(Entity damager) {
      if (damager instanceof Player player) {
         return player;
      } else {
         if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
               return player;
            }
         }

         return null;
      }
   }

   public void reset() {
      this.lastPlayerDamage.clear();
   }

   public static record DamageCredit(UUID playerId, String playerName, long damageTimeMillis) {
      static DamageCredit of(Player player) {
         return new DamageCredit(player.getUniqueId(), player.getName(), System.currentTimeMillis());
      }
   }
}
