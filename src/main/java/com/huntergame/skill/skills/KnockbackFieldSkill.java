package com.huntergame.skill.skills;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

public final class KnockbackFieldSkill implements HunterSkill {
    private static final String NAME = "击退领域";

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

        double radius = context.doubleParam(NAME, "radius", 10.0);
        double horizontalStrength = context.doubleParam(NAME, "horizontal_strength", 9.0);
        double verticalStrength = context.doubleParam(NAME, "vertical_strength", 1.5);

        player.sendTitle(
                context.message("skill_knockback_field_title", "&b🛡 &l击退领域 &r🛡"),
                context.message("skill_knockback_field_subtitle", ""),
                10, 60, 10
        );
        Collection<Player> nearbyPlayers = player.getWorld().getNearbyPlayers(
                player.getLocation(),
                radius,
                radius,
                radius,
                target -> target != player && target.getGameMode() == GameMode.SURVIVAL
        );

        for (Player target : nearbyPlayers) {
            Vector direction = target.getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize()
                    .multiply(horizontalStrength)
                    .setY(verticalStrength);
            target.setVelocity(direction);
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.8f);
        player.spawnParticle(Particle.EXPLOSION, player.getLocation(), 3);
        context.sendActivationMessage(player);
        return SkillActivationResult.success();
    }
}

