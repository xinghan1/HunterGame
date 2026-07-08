package com.huntergame.skill.skills;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class BlinkSkill implements HunterSkill {
   private static final String NAME = "\u95ea\u73b0";
   private final Map<UUID, Boolean> remoteImmune = new ConcurrentHashMap();

   public String getName() {
      return "\u95ea\u73b0";
   }

   public SkillActivationResult activate(final Player player, final SkillContext context) {
      int distance = context.intParam("\u95ea\u73b0", "hunter_distance", 40);
      if (context.plugin().isEscaper(player.getUniqueId())) {
         distance = context.intParam("\u95ea\u73b0", "escaper_distance", 50);
      }

      final Location targetLocation = this.findTargetLocation(player, distance);
      player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, (double)0.5F, (double)0.5F, (double)0.5F, 0.1);
      (new BukkitRunnable() {
         public void run() {
            if (player.isOnline()) {
               BlinkSkill.this.playTeleportEffects(player, player.getLocation(), targetLocation);
               player.teleport(targetLocation);
               player.sendTitle(context.message("skill_blink_title", "&b\ud83d\udcc8 &l\u95ea\u73b0 &r\ud83d\udcc8"), context.message("skill_blink_subtitle", ""), 10, 30, 10);
               BlinkSkill.this.remoteImmune.put(player.getUniqueId(), true);
               player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, context.intParam("\u95ea\u73b0", "resistance_ticks", 200), context.intParam("\u95ea\u73b0", "resistance_amplifier", 100), true, false));
               player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, context.intParam("\u95ea\u73b0", "speed_ticks", 200), context.intParam("\u95ea\u73b0", "speed_amplifier", 1), true, false));
            }
         }
      }).runTaskLater(context.plugin(), 0L);
      return SkillActivationResult.success();
   }

   public void onEntityDamage(EntityDamageEvent event, SkillContext context) {
      if (event.getEntity() instanceof Player) {
         Player player = (Player)event.getEntity();
         if ((Boolean)this.remoteImmune.getOrDefault(player.getUniqueId(), false) && this.isRemoteWeaponDamage(event)) {
            event.setCancelled(true);
         }

      }
   }

   public void onPlayerRespawn(PlayerRespawnEvent event, SkillContext context) {
      UUID uuid = event.getPlayer().getUniqueId();
      if ((Boolean)this.remoteImmune.getOrDefault(uuid, false)) {
         this.remoteImmune.put(uuid, true);
      }

   }

   public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
      this.remoteImmune.remove(event.getPlayer().getUniqueId());
   }

   private Location findTargetLocation(Player player, int maxDistance) {
      Location start = player.getLocation();
      Vector direction = player.getEyeLocation().getDirection();
      if (direction.getY() < (double)0.0F) {
         direction.setY(0);
      }

      if (direction.lengthSquared() == (double)0.0F) {
         direction = this.yawToHorizontalDirection(start.getYaw());
      } else {
         direction.normalize();
      }

      Location target = start.clone().add(direction.multiply(maxDistance));
      if (target.getY() < start.getY()) {
         target.setY(start.getY());
      }

      target.setYaw(start.getYaw());
      target.setPitch(start.getPitch());
      return target;
   }

   private Vector yawToHorizontalDirection(float yaw) {
      double radians = Math.toRadians((double)yaw);
      return new Vector(-Math.sin(radians), (double)0.0F, Math.cos(radians));
   }

   private void playTeleportEffects(Player player, Location from, Location to) {
      from.getWorld().spawnParticle(Particle.PORTAL, from, 50, (double)0.5F, (double)0.5F, (double)0.5F, 0.1);
      from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
      to.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, to, 3, 0.2, 0.2, 0.2, 0.1);
      to.getWorld().spawnParticle(Particle.PORTAL, to, 50, (double)0.5F, (double)0.5F, (double)0.5F, 0.1);
      to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
      player.spawnParticle(Particle.END_ROD, player.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
   }

   private boolean isRemoteWeaponDamage(EntityDamageEvent event) {
      EntityDamageEvent.DamageCause cause = event.getCause();
      if (cause != DamageCause.PROJECTILE && cause != DamageCause.ENTITY_EXPLOSION) {
         if (!(event instanceof EntityDamageByEntityEvent)) {
            return false;
         } else {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent)event;
            Entity damager = damageEvent.getDamager();
            if (damager instanceof Projectile) {
               Projectile projectile = (Projectile)damager;
               Object shooter = projectile.getShooter();
               return shooter instanceof Player && shooter != event.getEntity();
            } else {
               String damagerType = damager.getType().name();
               return damagerType.contains("ARROW") || damagerType.contains("SNOWBALL") || damagerType.contains("EGG") || damagerType.contains("TRIDENT");
            }
         }
      } else {
         return true;
      }
   }
}
