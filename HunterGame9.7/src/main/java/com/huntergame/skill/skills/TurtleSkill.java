package com.huntergame.skill.skills;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class TurtleSkill implements HunterSkill {
   private static final String NAME = "\u795e\u9f9f";

   public String getName() {
      return "\u795e\u9f9f";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      int duration = context.duration("\u795e\u9f9f");
      player.sendTitle(context.message("skill_turtle_title", "&b\ud83d\udee1 &l\u795e\u9f9f &r\ud83d\udee1"), context.message("skill_duration_subtitle", "&7\u6301\u7eed\u65f6\u95f4: &a%seconds%\u79d2", "%seconds%", duration), 10, 60, 10);
      player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, duration * 20, context.intParam("\u795e\u9f9f", "resistance_amplifier", 3)));
      player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration * 20, context.intParam("\u795e\u9f9f", "slowness_amplifier", 0)));
      context.sendActivationMessage(player);
      return SkillActivationResult.successWithDuration(duration);
   }
}
