package com.huntergame.session;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 掉线保护记录
public class DisconnectProtectionService implements Listener {
    private final HunterGame plugin;

    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    final Map<UUID, Boolean> playerRoles = new ConcurrentHashMap<>(); // true=逃生者, false=猎人
    private final Map<UUID, Long> playerDisconnectTime = new ConcurrentHashMap<>();

    private int cleanupTaskId = -1;
    private static final long CLEANUP_DELAY_MILLIS = 3 * 60 * 1000L; // 3分钟

    public DisconnectProtectionService(HunterGame plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startCleanupTask();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.isGameRunning()) {
            cleanupPlayerData(event.getPlayer().getUniqueId());
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId(); // 使用UUID作为键

        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        boolean isEscaper = plugin.isEscaper(playerId);
        playerRoles.put(playerId, isEscaper);
        lastLocations.put(playerId, player.getLocation().clone());
        playerDisconnectTime.put(playerId, System.currentTimeMillis());

        plugin.getLogger().info("玩家 " + player.getName() + " 已离线，角色 (" + (isEscaper ? "逃生者" : "猎人") + ") 已记录");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.isGameRunning()) {
            cleanupPlayerData(uuid);
            return;
        }

        // 检查是否存在玩家的离线角色记录
        if (playerRoles.containsKey(uuid)) {
            Boolean wasEscaper = playerRoles.get(uuid);

            if (wasEscaper == null) {
                plugin.getLogger().warning("玩家 " + player.getName() + " 的角色记录异常！");
                cleanupPlayerData(uuid);
                player.sendMessage(plugin.getMessage("restore_failed", "离线数据异常，未能恢复角色"));
                return;
            }

            // 恢复角色
            if (wasEscaper) {
                plugin.addEscaper(uuid);
                player.sendMessage(plugin.getMessage("recover_escape", "&a欢迎回来！已恢复逃生者角色"));
            } else {
                plugin.addHunter(uuid);
                player.sendMessage(plugin.getMessage("recover_hunter", "&a欢迎回来！已恢复猎人角色"));
            }

            // 恢复位置
            Location loc = lastLocations.get(uuid);
            if (loc != null) {
                player.teleport(loc);
            }
            plugin.getLogger().info("玩家 " + player.getName() + " 已恢复离线数据");
        }
    }

    public void cleanupPlayerData(UUID uuid) {
        // 清理技能记录
        plugin.getSkillManager().escapeeSkills.remove(uuid);
        plugin.getSkillManager().hunterSkills.remove(uuid);
        clearOfflineProtectionData(uuid);
    }

    public void clearOfflineProtectionData(UUID uuid) {
        // 清理离线数据
        playerRoles.remove(uuid);
        lastLocations.remove(uuid);
        playerDisconnectTime.remove(uuid);
    }

    private void startCleanupTask() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
        }
        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, Long>> iterator = playerDisconnectTime.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                UUID uuid = entry.getKey();
                long disconnectTime = entry.getValue();

                if (currentTime - disconnectTime > CLEANUP_DELAY_MILLIS) {
                    String playerName = uuid.toString();
                    plugin.getLogger().info("玩家 " + playerName + " 离线超过3分钟，清理数据");

                    cleanupPlayerData(uuid);

                    iterator.remove();
                }
            }
        }, 20L, 20L);
    }

    public void cleanup() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }

        clearAllData();
    }

    public void clearAllData() {
        lastLocations.clear();
        playerRoles.clear();
        playerDisconnectTime.clear();
    }

    public boolean hasOfflineProtectionData(UUID playerId) {
        return playerRoles.containsKey(playerId);
    }
}


