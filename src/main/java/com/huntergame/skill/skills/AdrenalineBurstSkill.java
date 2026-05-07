package com.huntergame.skill.skills;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdrenalineBurstSkill implements HunterSkill {
    private static final String NAME = "肾上腺爆发";

    private final Map<UUID, Integer> speedTasks = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        UUID uuid = player.getUniqueId();
        cancelSpeedTask(uuid);

        int effectSeconds = context.duration(NAME);
        if (context.plugin().isEscaper(uuid)) {
            effectSeconds += context.intParam(NAME, "escaper_extra_seconds", 5);
        }
        int effectTicks = effectSeconds * 20;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                effectTicks,
                context.intParam(NAME, "speed_amplifier", 4),
                true,
                true
        ));

        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.5f);
        player.sendTitle(
                context.message("skill_adrenaline_title", "&b⚡ &l肾上腺爆发&r⚡"),
                context.message("skill_duration_subtitle", "&7持续时间: &a%seconds%秒", "%seconds%", effectSeconds),
                10, 60, 10
        );

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(context.plugin(), () -> {
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    player.getLocation().add(0, 0.2, 0),
                    5,
                    0.1,
                    0.1,
                    0.1,
                    new Particle.DustOptions(Color.fromRGB(0, 0, 255), 1.5f)
            );
            player.getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    player.getLocation().add(0, 1, 0),
                    3,
                    0.3,
                    0.5,
                    0.3,
                    0.1
            );
        }, 0L, 2L);
        speedTasks.put(uuid, taskId);

        Bukkit.getScheduler().runTaskLater(context.plugin(), () -> {
            cancelSpeedTask(uuid);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    context.intParam(NAME, "slowness_after_seconds", 3) * 20,
                    context.intParam(NAME, "slowness_after_amplifier", 1),
                    true,
                    true
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
        }, effectTicks);

        context.sendActivationMessage(player);
        return SkillActivationResult.successWithDuration(effectSeconds);
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
        cancelSpeedTask(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
        Player player = event.getEntity();
        cancelSpeedTask(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    private void cancelSpeedTask(UUID uuid) {
        Integer taskId = speedTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}

