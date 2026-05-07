package com.huntergame.skill.skills;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezeSkill implements Listener {
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
                Location originalLoc = target.getLocation().clone();
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
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE,
                        durationTicks,
                        resistanceAmplifier,
                        true,
                        false
                ));

                frozenPlayers.put(targetId, new FreezeData(hunterId, durationTicks, originalLoc));
                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> unfreezePlayer(targetId), durationTicks).getTaskId();
                freezeTasks.put(targetId, taskId);
            }
        }
    }

    private void unfreezePlayer(UUID playerId) {
        if (!frozenPlayers.containsKey(playerId)) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.removeMetadata("frozen_by_perspective", plugin);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
        }

        Integer taskId = freezeTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        frozenPlayers.remove(playerId);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!isPlayerFrozen(playerId)) {
            return;
        }

        FreezeData freezeData = frozenPlayers.get(playerId);
        if (freezeData == null) {
            return;
        }

        Location originalLoc = freezeData.getOriginalLocation();
        if (isPositionChanged(event.getTo(), originalLoc)) {
            event.setCancelled(true);
            player.teleport(originalLoc);
            player.setRotation(originalLoc.getYaw(), originalLoc.getPitch());
        }
    }

    public boolean isPlayerFrozen(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return frozenPlayers.containsKey(playerId)
                && player != null
                && player.hasMetadata("frozen_by_perspective");
    }

    private boolean isPositionChanged(Location newLoc, Location originalLoc) {
        if (newLoc == null) {
            return true;
        }

        double tolerance = 0.01;
        return Math.abs(newLoc.getX() - originalLoc.getX()) > tolerance
                || Math.abs(newLoc.getY() - originalLoc.getY()) > tolerance
                || Math.abs(newLoc.getZ() - originalLoc.getZ()) > tolerance
                || Math.abs(newLoc.getYaw() - originalLoc.getYaw()) > 1
                || Math.abs(newLoc.getPitch() - originalLoc.getPitch()) > 1;
    }

    private static class FreezeData {
        private final UUID hunterId;
        private final int durationTicks;
        private final Location originalLocation;

        FreezeData(UUID hunterId, int durationTicks, Location originalLocation) {
            this.hunterId = hunterId;
            this.durationTicks = durationTicks;
            this.originalLocation = originalLocation;
        }

        public UUID getHunterId() {
            return hunterId;
        }

        public int getDurationTicks() {
            return durationTicks;
        }

        public Location getOriginalLocation() {
            return originalLocation;
        }
    }
}

