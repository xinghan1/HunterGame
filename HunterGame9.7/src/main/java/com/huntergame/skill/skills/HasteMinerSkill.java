package com.huntergame.skill.skills;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HasteMinerSkill implements HunterSkill {
   private static final String NAME = "\u76fe\u6784\u673a";

   public String getName() {
      return "\u76fe\u6784\u673a";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      int duration = context.duration("\u76fe\u6784\u673a");
      player.sendTitle(context.message("skill_haste_miner_title", "&b\u26a1 &l\u76fe\u6784\u673a &r\u26a1"), context.message("skill_duration_subtitle", "&7\u6301\u7eed\u65f6\u95f4: &a%seconds%\u79d2", "%seconds%", duration), 10, 40, 10);
      player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, duration * 20, context.intParam("\u76fe\u6784\u673a", "haste_amplifier", 254)));
      context.sendActivationMessage(player);
      return SkillActivationResult.successWithDuration(duration);
   }
}
