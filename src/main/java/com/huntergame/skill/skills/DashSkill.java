package com.huntergame.skill.skills;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class DashSkill implements HunterSkill {
    private static final String NAME = "突进";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canActivateWithItem(ItemStack item, SkillContext context) {
        return isSpear(item);
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        double forwardStrength = context.doubleParam(NAME, "forward_strength", 3.0);
        double upwardStrength = context.doubleParam(NAME, "upward_strength", 0.5);
        int delayTicks = getDelayTicks(context);
        int accelerationTicks = Math.max(1, context.intParam(NAME, "acceleration_ticks", 4));

        Vector direction = player.getLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() == 0) {
            direction = yawToHorizontalDirection(player.getLocation().getYaw());
        } else {
            direction.normalize();
        }

        scheduleDash(player, context, direction, forwardStrength, upwardStrength, delayTicks, accelerationTicks);
        player.sendTitle(
                context.message("skill_dash_title", "&b➤ &l突进 &r➤"),
                context.message("skill_dash_subtitle", ""),
                10, 30, 10
        );
        context.sendActivationMessage(player);
        return SkillActivationResult.success();
    }

    private void scheduleDash(Player player, SkillContext context, Vector direction, double forwardStrength,
                              double upwardStrength, int delayTicks, int accelerationTicks) {
        if (accelerationTicks <= 1) {
            Vector velocity = direction.clone().multiply(forwardStrength).setY(upwardStrength);
            Bukkit.getScheduler().runTaskLater(context.plugin(), () -> dash(player, velocity, true), delayTicks);
            return;
        }

        double weightSum = accelerationTicks * (accelerationTicks + 1) / 2.0;
        for (int step = 1; step <= accelerationTicks; step++) {
            int currentStep = step;
            long runDelay = delayTicks + step - 1L;
            Bukkit.getScheduler().runTaskLater(context.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }

                double stepForwardStrength = forwardStrength * currentStep / weightSum;
                Vector velocity = direction.clone().multiply(stepForwardStrength);
                velocity.setY(currentStep == 1 ? upwardStrength : player.getVelocity().getY());
                dash(player, velocity, currentStep == 1);
            }, runDelay);
        }
    }

    private int getDelayTicks(SkillContext context) {
        double delaySeconds = context.doubleParam(NAME, "delay_seconds", -1.0);
        if (delaySeconds >= 0.0) {
            return Math.max(0, (int) Math.round(delaySeconds * 20.0));
        }
        return Math.max(0, context.intParam(NAME, "delay_ticks", 10));
    }

    private void dash(Player player, Vector velocity, boolean playSound) {
        if (!player.isOnline()) {
            return;
        }

        player.setVelocity(velocity);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 16, 0.45, 0.25, 0.45, 0.08);
        if (playSound) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.35f);
        }
    }

    private boolean isSpear(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        String materialName = item.getType().name();
        return materialName.contains("SPEAR");
    }

    private Vector yawToHorizontalDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0, Math.cos(radians));
    }
}

