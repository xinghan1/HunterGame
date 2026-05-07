package com.huntergame.skill.skills;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;

public final class DemolitionExpertSkill implements HunterSkill {
    private static final String NAME = "爆破专家";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        if (context.isEndGlobalCooldownActive()) {
            player.sendMessage(context.plugin().getMessage("skill_disabled", "&c技能暂时被禁用!"));
            return SkillActivationResult.failure();
        }

        player.sendTitle(
                context.message("skill_demolition_title", "&b⚡ &l爆破专家 &r⚡"),
                context.message("skill_demolition_subtitle", "&7"),
                10, 30, 10
        );

        Location spawnLoc = player.getLocation().clone();
        Vector direction = spawnLoc.getDirection();
        double forwardOffset = context.doubleParam(NAME, "forward_offset", 1.2);
        double yOffset = context.doubleParam(NAME, "y_offset", 0.8);
        int tntCount = Math.max(1, context.intParam(NAME, "tnt_count", 3));
        List<Integer> fuseTicks = context.integerListParam(NAME, "fuse_ticks", Arrays.asList(60, 50, 65));

        spawnLoc.add(direction.multiply(forwardOffset));
        spawnLoc.setY(spawnLoc.getY() + yOffset);

        for (int i = 0; i < tntCount; i++) {
            TNTPrimed tnt = player.getWorld().spawn(spawnLoc, TNTPrimed.class);
            int fuse = fuseTicks.get(Math.min(i, fuseTicks.size() - 1));
            tnt.setFuseTicks(fuse);
        }

        context.sendActivationMessage(player);
        return SkillActivationResult.success();
    }
}

