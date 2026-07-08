package com.huntergame.skill.skills;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class DashSkill implements HunterSkill {
   private static final String NAME = "\u7a81\u8fdb";

   public String getName() {
      return "\u7a81\u8fdb";
   }

   public boolean canActivateWithItem(ItemStack item, SkillContext context) {
      return this.isSpear(item);
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      double forwardStrength = context.doubleParam("\u7a81\u8fdb", "forward_strength", (double)3.0F);
      double upwardStrength = context.doubleParam("\u7a81\u8fdb", "upward_strength", (double)0.5F);
      int delayTicks = this.getDelayTicks(context);
      int accelerationTicks = Math.max(1, context.intParam("\u7a81\u8fdb", "acceleration_ticks", 4));
      Vector direction = player.getLocation().getDirection();
      direction.setY(0);
      if (direction.lengthSquared() == (double)0.0F) {
         direction = this.yawToHorizontalDirection(player.getLocation().getYaw());
      } else {
         direction.normalize();
      }

      this.scheduleDash(player, context, direction, forwardStrength, upwardStrength, delayTicks, accelerationTicks);
      player.sendTitle(context.message("skill_dash_title", "&b\u27a4 &l\u7a81\u8fdb &r\u27a4"), context.message("skill_dash_subtitle", ""), 10, 30, 10);
      context.sendActivationMessage(player);
      return SkillActivationResult.success();
   }

   private void scheduleDash(Player player, SkillContext context, Vector direction, double forwardStrength, double upwardStrength, int delayTicks, int accelerationTicks) {
      if (accelerationTicks <= 1) {
         Vector velocity = direction.clone().multiply(forwardStrength).setY(upwardStrength);
         Bukkit.getScheduler().runTaskLater(context.plugin(), () -> this.dash(player, velocity, true), (long)delayTicks);
      } else {
         double weightSum = (double)(accelerationTicks * (accelerationTicks + 1)) / (double)2.0F;

         for(int step = 1; step <= accelerationTicks; ++step) {
            final int currentStep = step;
            long runDelay = (long)(delayTicks + currentStep) - 1L;
            Bukkit.getScheduler().runTaskLater(context.plugin(), () -> {
               if (player.isOnline()) {
                  double stepForwardStrength = forwardStrength * (double)currentStep / weightSum;
                  Vector velocity = direction.clone().multiply(stepForwardStrength);
                  velocity.setY(currentStep == 1 ? upwardStrength : player.getVelocity().getY());
                  this.dash(player, velocity, currentStep == 1);
               }
            }, runDelay);
         }

      }
   }

   private int getDelayTicks(SkillContext context) {
      double delaySeconds = context.doubleParam("\u7a81\u8fdb", "delay_seconds", (double)-1.0F);
      return delaySeconds >= (double)0.0F ? Math.max(0, (int)Math.round(delaySeconds * (double)20.0F)) : Math.max(0, context.intParam("\u7a81\u8fdb", "delay_ticks", 10));
   }

   private void dash(Player player, Vector velocity, boolean playSound) {
      if (player.isOnline()) {
         player.setVelocity(velocity);
         player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 16, 0.45, (double)0.25F, 0.45, 0.08);
         if (playSound) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0F, 1.35F);
         }

      }
   }

   private boolean isSpear(ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         String materialName = item.getType().name();
         return materialName.contains("SPEAR");
      } else {
         return false;
      }
   }

   private Vector yawToHorizontalDirection(float yaw) {
      double radians = Math.toRadians((double)yaw);
      return new Vector(-Math.sin(radians), (double)0.0F, Math.cos(radians));
   }
}
