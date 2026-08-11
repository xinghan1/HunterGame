package com.huntergame.portal;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class EndPortalTracker implements Listener {

    private static final int BLOCKS_PER_TICK = 900;

    private final HunterGame plugin;
    private boolean speedGranted = false;
    private boolean searchRunning = false;
    private Location portalLocation;
    private BukkitTask searchTask;

    public EndPortalTracker(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnderEyeInserted(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.END_PORTAL_FRAME
                || event.getItem() == null
                || event.getItem().getType() != Material.ENDER_EYE) {
            return;
        }

        Location frameLocation = event.getClickedBlock().getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> detectActivatedPortal(frameLocation), 1L);
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
            publishPortal(findPortalCenter(nearest, 4));
        }
        searchRunning = false;
        searchTask = null;
    }

    private void detectActivatedPortal(Location frameLocation) {
        Location center = findPortalCenter(frameLocation, 6);
        if (center != null) {
            publishPortal(center);
        }
    }

    private Location findPortalCenter(Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) return null;

        List<Block> portalBlocks = new ArrayList<>();
        for (int x = origin.getBlockX() - radius; x <= origin.getBlockX() + radius; x++) {
            for (int y = origin.getBlockY() - 1; y <= origin.getBlockY() + 1; y++) {
                for (int z = origin.getBlockZ() - radius; z <= origin.getBlockZ() + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.END_PORTAL) portalBlocks.add(block);
                }
            }
        }
        if (portalBlocks.isEmpty()) return null;

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Block block : portalBlocks) {
            x += block.getX();
            y += block.getY();
            z += block.getZ();
        }
        int count = portalBlocks.size();
        return new Location(world, Math.round(x / count), Math.round(y / count), Math.round(z / count));
    }

    private void publishPortal(Location location) {
        if (location == null || portalLocation != null) return;
        portalLocation = location;
        grantHuntersSpeed();

        Bukkit.broadcastMessage(plugin.getMessage(
                        "end_portal_activated",
                        "&5末地传送门已激活！ &f坐标: &e%x%, %y%, %z%")
                .replace("%world%", location.getWorld() == null ? "unknown" : location.getWorld().getName())
                .replace("%x%", String.valueOf(location.getBlockX()))
                .replace("%y%", String.valueOf(location.getBlockY()))
                .replace("%z%", String.valueOf(location.getBlockZ()))
                .replace("%coordinates%", formatBlockLocation(location)));
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

