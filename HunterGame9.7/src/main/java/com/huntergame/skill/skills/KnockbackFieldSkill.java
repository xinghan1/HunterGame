package com.huntergame.skill.skills;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class KnockbackFieldSkill implements HunterSkill {
   private static final String NAME = "\u51fb\u9000\u9886\u57df";

   public String getName() {
      return "\u51fb\u9000\u9886\u57df";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      if (context.isEndGlobalCooldownActive()) {
         player.sendMessage(context.plugin().getMessage("skill_disabled", "&c\u6280\u80fd\u6682\u65f6\u88ab\u7981\u7528!"));
         return SkillActivationResult.failure();
      } else {
         double radius = context.doubleParam("\u51fb\u9000\u9886\u57df", "radius", (double)10.0F);
         double horizontalStrength = context.doubleParam("\u51fb\u9000\u9886\u57df", "horizontal_strength", (double)9.0F);
         double verticalStrength = context.doubleParam("\u51fb\u9000\u9886\u57df", "vertical_strength", (double)1.5F);
         player.sendTitle(context.message("skill_knockback_field_title", "&b\ud83d\udee1 &l\u51fb\u9000\u9886\u57df &r\ud83d\udee1"), context.message("skill_knockback_field_subtitle", ""), 10, 60, 10);

         for(Player target : player.getWorld().getNearbyPlayers(player.getLocation(), radius, radius, radius, (targetx) -> targetx != player && targetx.getGameMode() == GameMode.SURVIVAL)) {
            Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(horizontalStrength).setY(verticalStrength);
            target.setVelocity(direction);
         }

         player.getWorld().playSound(player.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0F, 0.8F);
         player.spawnParticle(Particle.EXPLOSION_NORMAL, player.getLocation(), 3);
         context.sendActivationMessage(player);
         return SkillActivationResult.success();
      }
   }
}
