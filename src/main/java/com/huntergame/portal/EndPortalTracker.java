package com.huntergame.portal;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class EndPortalTracker {

    private static final int BLOCKS_PER_TICK = 900;

    private final HunterGame plugin;
    private boolean speedGranted = false;
    private boolean searchRunning = false;
    private Location portalLocation;
    private BukkitTask searchTask;

    public EndPortalTracker(HunterGame plugin) {
        this.plugin = plugin;
    }

    public Location findAndSetNearestEndPortal(Player player, int radius) {
        if (portalLocation != null) {
            return portalLocation;
        }
        if (searchRunning) {
            return null;
        }

        Location center = player.getLocation().clone();
        if (center.getWorld() == null) {
            return null;
        }

        startSearch(center, radius);
        return null;
    }

    public String getPortalCoordinatesPlaceholder(Player player) {
        if (portalLocation != null) {
            return formatBlockLocation(portalLocation);
        }

        findAndSetNearestEndPortal(player, 20);
        return searchRunning ? "搜索中" : "未找到";
    }

    public void reset() {
        portalLocation = null;
        speedGranted = false;
        cancelSearch();
    }

    public void cancelSearch() {
        searchRunning = false;
        if (searchTask != null) {
            searchTask.cancel();
            searchTask = null;
        }
    }

    private void startSearch(Location center, int radius) {
        searchRunning = true;
        World world = center.getWorld();
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + radius);
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;

        searchTask = new BukkitRunnable() {
            private int x = minX;
            private int y = minY;
            private int z = minZ;
            private Location nearest;
            private double nearestDistanceSquared = Double.MAX_VALUE;

            @Override
            public void run() {
                int checked = 0;
                while (x <= maxX && checked < BLOCKS_PER_TICK) {
                    Location location = new Location(world, x, y, z);
                    if (location.getBlock().getType() == Material.END_PORTAL) {
                        double distanceSquared = center.distanceSquared(location);
                        if (distanceSquared < nearestDistanceSquared) {
                            nearestDistanceSquared = distanceSquared;
                            nearest = location;
                        }
                    }

                    advanceCursor();
                    checked++;
                }

                if (x > maxX) {
                    finishSearch(nearest);
                    cancel();
                }
            }

            private void advanceCursor() {
                z++;
                if (z <= maxZ) return;
                z = minZ;
                y++;
                if (y <= maxY) return;
                y = minY;
                x++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishSearch(Location nearest) {
        if (nearest != null) {
            portalLocation = nearest;
            grantHuntersSpeed();
        }
        searchRunning = false;
        searchTask = null;
    }

    private void grantHuntersSpeed() {
        if (speedGranted) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isHunter(player.getUniqueId())) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300 * 20, 1));
            }
        }
        speedGranted = true;
    }

    private String formatBlockLocation(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}

