package com.huntergame.skill.skills;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;

public final class DemolitionExpertSkill implements HunterSkill {
   private static final String NAME = "\u7206\u7834\u4e13\u5bb6";

   public String getName() {
      return "\u7206\u7834\u4e13\u5bb6";
   }

   public SkillActivationResult activate(Player player, SkillContext context) {
      if (context.isEndGlobalCooldownActive()) {
         player.sendMessage(context.plugin().getMessage("skill_disabled", "&c\u6280\u80fd\u6682\u65f6\u88ab\u7981\u7528!"));
         return SkillActivationResult.failure();
      } else {
         player.sendTitle(context.message("skill_demolition_title", "&b\u26a1 &l\u7206\u7834\u4e13\u5bb6 &r\u26a1"), context.message("skill_demolition_subtitle", "&7"), 10, 30, 10);
         Location spawnLoc = player.getLocation().clone();
         Vector direction = spawnLoc.getDirection();
         double forwardOffset = context.doubleParam("\u7206\u7834\u4e13\u5bb6", "forward_offset", 1.2);
         double yOffset = context.doubleParam("\u7206\u7834\u4e13\u5bb6", "y_offset", 0.8);
         int tntCount = Math.max(1, context.intParam("\u7206\u7834\u4e13\u5bb6", "tnt_count", 3));
         List<Integer> fuseTicks = context.integerListParam("\u7206\u7834\u4e13\u5bb6", "fuse_ticks", Arrays.asList(60, 50, 65));
         spawnLoc.add(direction.multiply(forwardOffset));
         spawnLoc.setY(spawnLoc.getY() + yOffset);

         for(int i = 0; i < tntCount; ++i) {
            TNTPrimed tnt = (TNTPrimed)player.getWorld().spawn(spawnLoc, TNTPrimed.class);
            int fuse = (Integer)fuseTicks.get(Math.min(i, fuseTicks.size() - 1));
            tnt.setFuseTicks(fuse);
         }

         context.sendActivationMessage(player);
         return SkillActivationResult.success();
      }
   }
}
