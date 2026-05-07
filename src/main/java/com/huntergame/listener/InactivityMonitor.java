package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InactivityMonitor {

    private final HunterGame plugin;
    private final Map<UUID, Long> lastActivityTime = new HashMap<>(); // 记录玩家最后活动时间
    private long inactivityKickTime; // 无操作踢出时间（毫秒）

    public InactivityMonitor(HunterGame plugin, long inactivityKickTime) {
        this.plugin = plugin;
        this.inactivityKickTime = inactivityKickTime;
        startInactivityCheckTask();
    }

    // 启动定时检查任务
    private void startInactivityCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) {
                    return;
                }
                long currentTime = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();
                    if (lastActivityTime.containsKey(playerId)) {
                        long inactiveTime = currentTime - lastActivityTime.get(playerId);
                        if (inactiveTime >= inactivityKickTime) {
                            kickForInactivity(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20 * 60); // 每分钟检查一次
    }

    // 踢出长时间无操作的玩家
    private void kickForInactivity(Player player) {
        player.kickPlayer(plugin.getMessage("inactivity_kick_message", "&c你因长时间无操作被踢出游戏！"));
        lastActivityTime.remove(player.getUniqueId());
        plugin.getLogger().info("玩家 " + player.getName() + " 因长时间无操作被踢出游戏。");
    }

    // 更新玩家的最后活动时间
    public void updateActivity(Player player) {
        lastActivityTime.put(player.getUniqueId(), System.currentTimeMillis());
    }
}


