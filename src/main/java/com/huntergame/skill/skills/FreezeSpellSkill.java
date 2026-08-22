package com.huntergame.skill.skills;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FreezeSpellSkill implements HunterSkill {
    private static final String NAME = "定身术";

    private final Map<UUID, Integer> glowTasks = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SkillActivationResult activate(Player player, SkillContext context) {
        int freezeTicks = context.intParam(NAME, "freeze_ticks", context.duration(NAME) * 20);
        int glowTicks = context.intParam(NAME, "glow_ticks", 1200);

        player.sendTitle(
                context.message("skill_freeze_spell_title", "&b👀 &l定身术 &r👀"),
                context.message("skill_duration_subtitle", "&7持续时间: &a%seconds%秒", "%seconds%", freezeTicks / 20),
                10, 60, 10
        );
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.HASTE,
                context.intParam(NAME, "haste_ticks", Integer.MAX_VALUE),
                context.intParam(NAME, "haste_amplifier", 2),
                true,
                true
        ));

        activateGlowEffect(player, context, glowTicks);
        context.plugin().getFreezeSkill().freezeAllEscapers(
                player,
                freezeTicks,
                context.intParam(NAME, "frozen_resistance_amplifier", 254)
        );

        UUID uuid = player.getUniqueId();
        cancelGlowTask(uuid);
        int taskId = Bukkit.getScheduler().runTaskLater(context.plugin(), () -> removeGlowEffect(context), glowTicks).getTaskId();
        glowTasks.put(uuid, taskId);

        context.sendActivationMessage(player);
        return SkillActivationResult.successWithDuration(context.duration(NAME));
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, SkillContext context) {
        cleanup(event.getPlayer(), context);
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event, SkillContext context) {
        cleanup(event.getEntity(), context);
    }

    private void activateGlowEffect(Player hunter, SkillContext context, int durationTicks) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!context.plugin().isEscaper(player.getUniqueId())) {
                continue;
            }

            if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                player.removePotionEffect(PotionEffectType.GLOWING);
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, true, false));
            hunter.showPlayer(context.plugin(), player);
            hunter.spawnParticle(
                    Particle.DUST,
                    player.getLocation(),
                    10,
                    new Particle.DustOptions(Color.fromRGB(0, 0, 0), 1.0f)
            );
        }
    }

    private void removeGlowEffect(SkillContext context) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (context.plugin().isEscaper(player.getUniqueId())) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    private void cleanup(Player player, SkillContext context) {
        cancelGlowTask(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.HASTE);
        removeGlowEffect(context);
    }

    private void cancelGlowTask(UUID uuid) {
        Integer taskId = glowTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}

