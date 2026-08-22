package com.huntergame.skill.skills;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezeSkill implements Listener {
    private static final int MAX_EFFECT_AMPLIFIER = 254;

    private final HunterGame plugin;
    private final Map<UUID, FreezeData> frozenPlayers = new HashMap<>();
    private final Map<UUID, Integer> freezeTasks = new HashMap<>();

    public FreezeSkill(HunterGame plugin) {
        this.plugin = plugin;
    }

    public void unfreezeNow(UUID playerId) {
        unfreezePlayer(playerId);
    }

    public void freezeEscapers(Player hunter, int durationTicks) {
        freezeEscapers(hunter, durationTicks, 40.0, 254);
    }

    public void freezeEscapers(Player hunter, int durationTicks, double radius, int resistanceAmplifier) {
        freezeEscapers(hunter, durationTicks, radius, resistanceAmplifier, true);
    }

    public void freezeAllEscapers(Player hunter, int durationTicks, int resistanceAmplifier) {
        freezeEscapers(hunter, durationTicks, 0.0, resistanceAmplifier, false);
    }

    private void freezeEscapers(Player hunter, int durationTicks, double radius, int resistanceAmplifier, boolean checkRadius) {
        UUID hunterId = hunter.getUniqueId();
        Location hunterLoc = hunter.getLocation();
        double radiusSquared = radius * radius;

        for (Player target : Bukkit.getOnlinePlayers()) {
            UUID targetId = target.getUniqueId();
            if (plugin.isEscaper(targetId)
                    && (!checkRadius || hunterLoc.distanceSquared(target.getLocation()) <= radiusSquared)
                    && !target.hasPotionEffect(PotionEffectType.INVISIBILITY)
                    && target.getGameMode() != GameMode.SPECTATOR) {
                FreezeData previousFreeze = frozenPlayers.get(targetId);
                Map<PotionEffectType, PotionEffect> previousEffects = previousFreeze == null
                        ? capturePreviousEffects(target)
                        : previousFreeze.getPreviousEffects();

                Integer previousTaskId = freezeTasks.remove(targetId);
                if (previousTaskId != null) {
                    Bukkit.getScheduler().cancelTask(previousTaskId);
                }

                target.setMetadata("frozen_by_perspective", new FixedMetadataValue(plugin, hunterId));
                target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0F, 1.5F);
                target.getWorld().spawnParticle(
                        Particle.END_ROD,
                        target.getLocation().add(0, 1, 0),
                        20,
                        0.5,
                        0.5,
                        0.5,
                        0.2
                );
                applyFreezeEffect(target, PotionEffectType.SLOWNESS, durationTicks, MAX_EFFECT_AMPLIFIER);
                applyFreezeEffect(target, PotionEffectType.BLINDNESS, durationTicks, 0);
                applyFreezeEffect(target, PotionEffectType.WEAKNESS, durationTicks, MAX_EFFECT_AMPLIFIER);
                applyFreezeEffect(target, PotionEffectType.RESISTANCE, durationTicks, resistanceAmplifier);

                frozenPlayers.put(targetId, new FreezeData(previousEffects));
                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> unfreezePlayer(targetId), durationTicks).getTaskId();
                freezeTasks.put(targetId, taskId);
            }
        }
    }

    private void applyFreezeEffect(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false), true);
    }

    private Map<PotionEffectType, PotionEffect> capturePreviousEffects(Player player) {
        Map<PotionEffectType, PotionEffect> previousEffects = new HashMap<>();
        rememberEffect(player, PotionEffectType.SLOWNESS, previousEffects);
        rememberEffect(player, PotionEffectType.BLINDNESS, previousEffects);
        rememberEffect(player, PotionEffectType.WEAKNESS, previousEffects);
        rememberEffect(player, PotionEffectType.RESISTANCE, previousEffects);
        return previousEffects;
    }

    private void rememberEffect(Player player, PotionEffectType type, Map<PotionEffectType, PotionEffect> effects) {
        PotionEffect effect = player.getPotionEffect(type);
        if (effect != null) {
            effects.put(type, effect);
        }
    }

    private void unfreezePlayer(UUID playerId) {
        if (!frozenPlayers.containsKey(playerId)) {
            return;
        }

        FreezeData freezeData = frozenPlayers.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.removeMetadata("frozen_by_perspective", plugin);
            removeFreezeEffect(player, PotionEffectType.SLOWNESS, freezeData);
            removeFreezeEffect(player, PotionEffectType.BLINDNESS, freezeData);
            removeFreezeEffect(player, PotionEffectType.WEAKNESS, freezeData);
            removeFreezeEffect(player, PotionEffectType.RESISTANCE, freezeData);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
        }

        Integer taskId = freezeTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void removeFreezeEffect(Player player, PotionEffectType type, FreezeData freezeData) {
        player.removePotionEffect(type);
        PotionEffect previousEffect = freezeData.getPreviousEffects().get(type);
        if (previousEffect != null) {
            player.addPotionEffect(previousEffect, true);
        }
    }

    public boolean isPlayerFrozen(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return frozenPlayers.containsKey(playerId)
                && player != null
                && player.hasMetadata("frozen_by_perspective");
    }

    private static class FreezeData {
        private final Map<PotionEffectType, PotionEffect> previousEffects;

        FreezeData(Map<PotionEffectType, PotionEffect> previousEffects) {
            this.previousEffects = previousEffects;
        }

        public Map<PotionEffectType, PotionEffect> getPreviousEffects() {
            return previousEffects;
        }
    }
}

