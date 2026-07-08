package com.huntergame.skill.skills;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class DoubleJumpSkill implements HunterSkill {
   private static final String NAME = "\u4e8c\u6bb5\u8df3";

   public String getName() {
      return "\u4e8c\u6bb5\u8df3";
   }

   public boolean canActivateByRightClick() {
      return false;
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      player.sendTitle(context.message("skill_double_jump_title", "&b\u26a1 &l\u4e8c\u6bb5\u8df3 &r\u26a1"), context.message("skill_double_jump_subtitle", ""), 10, 30, 10);
      context.sendActivationMessage(player);
      return SkillActivationResult.success();
   }

   public void onPlayerMove(PlayerMoveEvent event, SkillContext context) {
      if (event.getTo() != null && event.getFrom().getY() != event.getTo().getY()) {
         Player player = event.getPlayer();
         if (context.isSelected(player, "\u4e8c\u6bb5\u8df3")) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
               if (player.isOnGround()) {
                  player.setAllowFlight(true);
               }

            }
         }
      }
   }

   public void onPlayerToggleFlight(PlayerToggleFlightEvent event, SkillContext context) {
      final Player player = event.getPlayer();
      if (context.isSelected(player, "\u4e8c\u6bb5\u8df3")) {
         if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            (new BukkitRunnable() {
               public void run() {
                  player.setAllowFlight(false);
               }
            }).runTaskLater(context.plugin(), 20L);
            if (context.checkCooldown(player, "\u4e8c\u6bb5\u8df3")) {
               this.performDoubleJump(player, context);
               context.startCooldown(player, "\u4e8c\u6bb5\u8df3");
            }
         }
      }
   }

   private void performDoubleJump(final Player player, SkillContext context) {
      double verticalPower = context.doubleParam("\u4e8c\u6bb5\u8df3", "hunter_vertical_power", (double)1.0F);
      double horizontalPower = context.doubleParam("\u4e8c\u6bb5\u8df3", "hunter_horizontal_power", (double)1.5F);
      if (context.plugin().isEscaper(player.getUniqueId())) {
         verticalPower = context.doubleParam("\u4e8c\u6bb5\u8df3", "escaper_vertical_power", 1.3);
         horizontalPower = context.doubleParam("\u4e8c\u6bb5\u8df3", "escaper_horizontal_power", 1.8);
      }

      player.setVelocity(player.getLocation().getDirection().multiply(horizontalPower).setY(verticalPower));
      player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, (double)0.5F, (double)0.5F, (double)0.5F, 0.15);
      player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0F, 1.5F);
      (new BukkitRunnable() {
         public void run() {
            player.setAllowFlight(false);
         }
      }).runTaskLater(context.plugin(), (long)context.intParam("\u4e8c\u6bb5\u8df3", "reset_flight_ticks", 40));
   }
}
