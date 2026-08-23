package com.huntergame.skill.skills;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class AirLaunchSkill implements HunterSkill {
    private static final String NAME = "腾空";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canActivateWithItem(ItemStack item, SkillContext context) {
        return item != null && item.getType() == Material.MACE;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        double verticalVelocity = Math.max(0.1, context.doubleParam(NAME, "vertical_velocity", 1.45));

        Vector velocity = player.getVelocity().clone();
        velocity.setY(verticalVelocity);
        player.setFallDistance(0.0F);
        player.setVelocity(velocity);

        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 24, 0.55, 0.15, 0.55, 0.12);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1.0F, 1.1F);
        player.sendTitle(
                context.message("skill_air_launch_title", "&b↑ &l腾空 &r↑"),
                "",
                5, 30, 10
        );
        context.sendActivationMessage(player);
        return SkillActivationResult.success();
    }
}
