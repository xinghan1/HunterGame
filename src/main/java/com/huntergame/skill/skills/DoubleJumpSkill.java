package com.huntergame.skill.skills;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class DoubleJumpSkill implements HunterSkill {
    private static final String NAME = "二段跳";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canActivateByRightClick() {
        return false;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        player.sendTitle(
                context.message("skill_double_jump_title", "&b⚡ &l二段跳 &r⚡"),
                context.message("skill_double_jump_subtitle", ""),
                10, 30, 10
        );
        context.sendActivationMessage(player);
        return SkillActivationResult.success();
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, SkillContext context) {
        if (event.getTo() == null || event.getFrom().getY() == event.getTo().getY()) {
            return;
        }

        Player player = event.getPlayer();
        if (!context.isSelected(player, NAME)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (player.isOnGround()) {
            player.setAllowFlight(true);
        }
    }

    @Override
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event, SkillContext context) {
        Player player = event.getPlayer();
        if (!context.isSelected(player, NAME)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        event.setCancelled(true);
        player.setAllowFlight(false);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.setAllowFlight(false);
            }
        }.runTaskLater(context.plugin(), 20L);

        if (!context.checkCooldown(player, NAME)) {
            return;
        }

        performDoubleJump(player, context);
        context.startCooldown(player, NAME);
    }

    private void performDoubleJump(Player player, SkillContext context) {
        double verticalPower = context.doubleParam(NAME, "hunter_vertical_power", 1.0);
        double horizontalPower = context.doubleParam(NAME, "hunter_horizontal_power", 1.5);

        if (context.plugin().isEscaper(player.getUniqueId())) {
            verticalPower = context.doubleParam(NAME, "escaper_vertical_power", 1.3);
            horizontalPower = context.doubleParam(NAME, "escaper_horizontal_power", 1.8);
        }

        player.setVelocity(player.getLocation().getDirection().multiply(horizontalPower).setY(verticalPower));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.5, 0.5, 0.5, 0.15);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.setAllowFlight(false);
            }
        }.runTaskLater(context.plugin(), context.intParam(NAME, "reset_flight_ticks", 40));
    }
}

