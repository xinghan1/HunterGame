package com.huntergame.skill.skills;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HasteMinerSkill implements HunterSkill {
    private static final String NAME = "盾构机";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        int duration = context.duration(NAME);
        player.sendTitle(
                context.message("skill_haste_miner_title", "&b⚡ &l盾构机 &r⚡"),
                context.message("skill_duration_subtitle", "&7持续时间: &a%seconds%秒", "%seconds%", duration),
                10, 40, 10
        );
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.HASTE,
                duration * 20,
                context.intParam(NAME, "haste_amplifier", 254)
        ));

        context.sendActivationMessage(player);
        return SkillActivationResult.successWithDuration(duration);
    }
}

