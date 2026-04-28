package com.huntergame.spectator;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpectatorService {

    private static final Particle.DustOptions DIRECTION_PARTICLE =
            new Particle.DustOptions(Color.BLUE, 1.0f);

    private final HunterGame plugin;
    private final Random random = new Random();

    public SpectatorService(HunterGame plugin) {
        this.plugin = plugin;
    }

    public void showHunterParticleDirection(Player hunter) {
        Player nearestEscaper = findNearestEscaper(hunter);
        if (nearestEscaper == null) {
            return;
        }

        Location hunterLocation = hunter.getLocation();
        Location escaperLocation = nearestEscaper.getLocation();
        org.bukkit.util.Vector direction = escaperLocation.toVector().subtract(hunterLocation.toVector()).normalize();

        Location particleLocation = hunterLocation.clone().add(0, 0.1, 0);
        for (double distance = 0; distance <= 2.0; distance += 0.2) {
            Location point = particleLocation.clone().add(direction.clone().multiply(distance));
            hunter.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, DIRECTION_PARTICLE);
        }
    }

    public void checkAndTeleportSpectators() {
        int maxDistance = plugin.getConfig().getInt("game.spectator_max_distance", 100);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR || !plugin.isRealSpectator(player.getUniqueId())) {
                continue;
            }

            Player spectatorTarget = getSpectatorTarget(player);
            if (spectatorTarget != null && spectatorTarget.isOnline()) {
                continue;
            }

            teleportBackIfTooFar(player, maxDistance);
        }
    }

    public void teleportToRandomPlayer(Player spectator) {
        List<Player> availablePlayers = new ArrayList<>();
        addActivePlayers(availablePlayers, plugin.getHunters());
        addActivePlayers(availablePlayers, plugin.getEscapers());

        if (!availablePlayers.isEmpty()) {
            Player target = availablePlayers.get(random.nextInt(availablePlayers.size()));
            spectator.teleport(target.getLocation());
        }
    }

    private Player findNearestEscaper(Player hunter) {
        Player nearestEscaper = null;
        double minDistance = Double.MAX_VALUE;

        for (Player escaper : plugin.getEscapers()) {
            if (!isActiveInSameWorld(escaper, hunter)) {
                continue;
            }

            double distance = hunter.getLocation().distance(escaper.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearestEscaper = escaper;
            }
        }

        return nearestEscaper;
    }

    private void teleportBackIfTooFar(Player spectator, int maxDistance) {
        Player nearestPlayer = findNearestPlayer(spectator);
        if (nearestPlayer == null) {
            return;
        }

        if (!spectator.getWorld().equals(nearestPlayer.getWorld())) {
            teleportNearPlayer(spectator, nearestPlayer);
            return;
        }

        try {
            double distance = spectator.getLocation().distance(nearestPlayer.getLocation());
            if (distance > maxDistance) {
                teleportNearPlayer(spectator, nearestPlayer);
            }
        } catch (IllegalArgumentException ignored) {
            teleportNearPlayer(spectator, nearestPlayer);
        }
    }

    private Player findNearestPlayer(Player spectator) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!isTeleportAnchor(spectator, target)) {
                continue;
            }

            if (target.getWorld().equals(spectator.getWorld())) {
                try {
                    double distance = spectator.getLocation().distance(target.getLocation());
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearest = target;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 不同世界的距离不能直接计算，下面会寻找任意有效目标。
                }
            }
        }

        if (nearest != null) {
            return nearest;
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (isTeleportAnchor(spectator, target)) {
                return target;
            }
        }

        return null;
    }

    private void teleportNearPlayer(Player spectator, Player target) {
        spectator.teleport(target.getLocation());
        spectator.sendMessage(ChatColor.GRAY + "不可以跑远了，已传送到 " + target.getName() + " 附近");
    }

    private Player getSpectatorTarget(Player spectator) {
        Entity target = spectator.getSpectatorTarget();
        return target instanceof Player ? (Player) target : null;
    }

    private void addActivePlayers(List<Player> availablePlayers, List<Player> players) {
        for (Player player : players) {
            if (player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                availablePlayers.add(player);
            }
        }
    }

    private boolean isActiveInSameWorld(Player player, Player reference) {
        return player != null
                && player.isOnline()
                && player.getGameMode() != GameMode.SPECTATOR
                && player.getWorld().equals(reference.getWorld());
    }

    private boolean isTeleportAnchor(Player spectator, Player target) {
        return !target.equals(spectator) && target.getGameMode() != GameMode.SPECTATOR;
    }
}
