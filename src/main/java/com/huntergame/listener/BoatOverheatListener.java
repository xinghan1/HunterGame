package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class BoatOverheatListener implements Listener {
    private final HunterGame plugin;
    private final Map<UUID, Double> overheatProgress = new HashMap<>();

    public BoatOverheatListener(HunterGame plugin) {
        this.plugin = plugin;
        startOverheatTask();
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!isEnabled() || !(event.getVehicle() instanceof Boat) || !(event.getEntered() instanceof Player player)) {
            return;
        }

        if (getProgress(player) >= getMaxProgress()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("boat_overheat_blocked", "&c你还很累，休息一会儿再划船吧！"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        overheatProgress.remove(event.getPlayer().getUniqueId());
    }

    private void startOverheatTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled()) {
                    overheatProgress.clear();
                    return;
                }

                updateOnlinePlayers();
                coolOfflineEntries();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void updateOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            double progress = getProgress(player);

            if (isPlayerInBoat(player)) {
                progress = Math.min(getMaxProgress(), progress + getHeatGainPerSecond());
                overheatProgress.put(playerId, progress);
                sendProgressSubtitle(player, progress);

                if (progress >= getMaxProgress()) {
                    forceDismount(player);
                }
                continue;
            }

            if (progress > 0.0) {
                progress = Math.max(0.0, progress - getCoolDownPerSecond());
                if (progress <= 0.0) {
                    overheatProgress.remove(playerId);
                } else {
                    overheatProgress.put(playerId, progress);
                }
            }
        }
    }

    private void coolOfflineEntries() {
        Iterator<Map.Entry<UUID, Double>> iterator = overheatProgress.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Double> entry = iterator.next();
            if (Bukkit.getPlayer(entry.getKey()) != null) {
                continue;
            }

            double progress = entry.getValue() - getCoolDownPerSecond();
            if (progress <= 0.0) {
                iterator.remove();
            } else {
                entry.setValue(progress);
            }
        }
    }

    private boolean isPlayerInBoat(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof Boat;
    }

    private void forceDismount(Player player) {
        player.leaveVehicle();
        player.sendMessage(plugin.getMessage("boat_overheat_forced", "&c你感到很累，需要下船休息一下！"));
    }

    private void sendProgressSubtitle(Player player, double progress) {
        player.sendTitle(
                "",
                plugin.getMessage("boat_overheat_subtitle", "&e疲劳程度: &c%progress%%")
                        .replace("%progress%", String.format("%.1f", progress)),
                0,
                25,
                5
        );
    }

    private double getProgress(Player player) {
        return overheatProgress.getOrDefault(player.getUniqueId(), 0.0);
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("game.boat_overheat.enabled", true);
    }

    private double getHeatGainPerSecond() {
        return Math.max(0.0, plugin.getConfig().getDouble("game.boat_overheat.heat_gain_per_second", 0.5));
    }

    private double getCoolDownPerSecond() {
        return Math.max(0.0, plugin.getConfig().getDouble("game.boat_overheat.cool_down_per_second", 1.0));
    }

    private double getMaxProgress() {
        return Math.max(1.0, plugin.getConfig().getDouble("game.boat_overheat.max_progress", 100.0));
    }
}
