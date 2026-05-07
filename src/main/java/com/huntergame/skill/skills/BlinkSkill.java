package com.huntergame.skill.skills;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlinkSkill implements HunterSkill {
    private static final String NAME = "闪现";

    private final Map<UUID, Boolean> remoteImmune = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        int distance = context.intParam(NAME, "hunter_distance", 40);
        if (context.plugin().isEscaper(player.getUniqueId())) {
            distance = context.intParam(NAME, "escaper_distance", 50);
        }

        Location targetLocation = findTargetLocation(player, distance);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }

                playTeleportEffects(player, player.getLocation(), targetLocation);
                player.teleport(targetLocation);
                player.sendTitle(
                        context.message("skill_blink_title", "&b📈 &l闪现 &r📈"),
                        context.message("skill_blink_subtitle", ""),
                        10, 30, 10
                );
                remoteImmune.put(player.getUniqueId(), true);
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE,
                        context.intParam(NAME, "resistance_ticks", 200),
                        context.intParam(NAME, "resistance_amplifier", 100),
                        true,
                        false
                ));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED,
                        context.intParam(NAME, "speed_ticks", 200),
                        context.intParam(NAME, "speed_amplifier", 1),
                        true,
                        false
                ));
            }
        }.runTaskLater(context.plugin(), 0L);

        return SkillActivationResult.success();
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, SkillContext context) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (remoteImmune.getOrDefault(player.getUniqueId(), false) && isRemoteWeaponDamage(event)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event, SkillContext context) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (remoteImmune.getOrDefault(uuid, false)) {
            remoteImmune.put(uuid, true);
        }
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
        remoteImmune.remove(event.getPlayer().getUniqueId());
    }

    private Location findTargetLocation(Player player, int maxDistance) {
        Location start = player.getLocation();
        Vector direction = player.getEyeLocation().getDirection();
        if (direction.getY() < 0) {
            direction.setY(0);
        }
        if (direction.lengthSquared() == 0) {
            direction = yawToHorizontalDirection(start.getYaw());
        } else {
            direction.normalize();
        }

        Location target = start.clone().add(direction.multiply(maxDistance));
        if (target.getY() < start.getY()) {
            target.setY(start.getY());
        }
        target.setYaw(start.getYaw());
        target.setPitch(start.getPitch());
        return target;
    }

    private Vector yawToHorizontalDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0, Math.cos(radians));
    }

    private void playTeleportEffects(Player player, Location from, Location to) {
        from.getWorld().spawnParticle(Particle.PORTAL, from, 50, 0.5, 0.5, 0.5, 0.1);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        to.getWorld().spawnParticle(Particle.EXPLOSION, to, 3, 0.2, 0.2, 0.2, 0.1);
        to.getWorld().spawnParticle(Particle.PORTAL, to, 50, 0.5, 0.5, 0.5, 0.1);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.spawnParticle(Particle.END_ROD, player.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
    }

    private boolean isRemoteWeaponDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return true;
        }

        if (!(event instanceof EntityDamageByEntityEvent)) {
            return false;
        }

        EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
        Entity damager = damageEvent.getDamager();
        if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            Object shooter = projectile.getShooter();
            return shooter instanceof Player && shooter != event.getEntity();
        }

        String damagerType = damager.getType().name();
        return damagerType.contains("ARROW")
                || damagerType.contains("SNOWBALL")
                || damagerType.contains("EGG")
                || damagerType.contains("TRIDENT");
    }
}

