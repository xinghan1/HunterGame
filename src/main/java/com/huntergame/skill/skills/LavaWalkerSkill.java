package com.huntergame.skill.skills;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public final class LavaWalkerSkill implements HunterSkill {
    private static final String NAME = "熔岩行者";

    private final Random random = new Random();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        Location playerLoc = player.getLocation();
        float yaw = playerLoc.getYaw();

        double lavaOffset = context.doubleParam(NAME, "lava_offset", 1.0);
        double x = playerLoc.getX() - Math.sin(Math.toRadians(yaw)) * lavaOffset;
        double z = playerLoc.getZ() + Math.cos(Math.toRadians(yaw)) * lavaOffset;
        double y = playerLoc.getY() + context.doubleParam(NAME, "lava_y_offset", 1.0);

        Block targetBlock = new Location(playerLoc.getWorld(), x, y, z).getBlock();
        targetBlock.setType(Material.LAVA);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE,
                context.intParam(NAME, "fire_resistance_ticks", Integer.MAX_VALUE),
                context.intParam(NAME, "fire_resistance_amplifier", 0),
                true,
                false
        ));

        player.sendTitle(
                context.message("skill_lava_walker_title", "&b💥 &l熔岩行者 &r💥"),
                context.message("skill_lava_walker_subtitle", "&7永久抗火效果已激活"),
                10, 60, 10
        );
        player.spawnParticle(Particle.LAVA, playerLoc, 10, 0.2, 0.2, 0.2, 0.1);
        context.sendActivationMessage(player);
        return SkillActivationResult.success();
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, SkillContext context) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        if (!context.isSelected(attacker, NAME)) {
            return;
        }

        double fireChance = context.doubleParam(NAME, "fire_chance", 0.20);
        if (random.nextDouble() >= fireChance) {
            return;
        }

        Entity victim = event.getEntity();
        if (victim instanceof LivingEntity) {
            ((LivingEntity) victim).setFireTicks(context.intParam(NAME, "fire_ticks", 60));
        }
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
        if (context.isSelected(event.getEntity(), NAME)) {
            event.getEntity().removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        }
    }
}

