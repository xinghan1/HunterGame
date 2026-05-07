package com.huntergame.skill.skills;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class TurtleSkill implements HunterSkill {
    private static final String NAME = "神龟";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        int duration = context.duration(NAME);
        player.sendTitle(
                context.message("skill_turtle_title", "&b🛡 &l神龟 &r🛡"),
                context.message("skill_duration_subtitle", "&7持续时间: &a%seconds%秒", "%seconds%", duration),
                10, 60, 10
        );
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                duration * 20,
                context.intParam(NAME, "resistance_amplifier", 3)
        ));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                duration * 20,
                context.intParam(NAME, "slowness_amplifier", 0)
        ));
        context.sendActivationMessage(player);
        return SkillActivationResult.successWithDuration(duration);
    }
}

