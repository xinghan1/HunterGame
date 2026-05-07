package com.huntergame.game;

import com.huntergame.HunterGame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CageManager {
    private final HunterGame plugin;
    private final Map<UUID, Set<Location>> playerCages = new ConcurrentHashMap<>();
    private final Set<Location> barrierBlocks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> taskIds = new ConcurrentHashMap<>();
    private final Set<Integer> groupTaskIds = ConcurrentHashMap.newKeySet();

    public CageManager(HunterGame plugin) {
        this.plugin = plugin;
    }

    /**
     * 判断玩家是否仍处于屏障中
     */
    public boolean isInCage(Player player) {
        return playerCages.containsKey(player.getUniqueId());
    }

    /**
     * 为一组玩家在同一地点生成共享屏障笼子（原版猎人模式）
     */
    public void createGroupCage(List<Player> players, Location center) {
        if (players == null || players.isEmpty()) return;

        Set<Location> cageBlocks = new HashSet<>();
        int radius = 2;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean isSurface = Math.abs(x) == radius || Math.abs(y) == radius || Math.abs(z) == radius;
                    Location loc = center.clone().add(x, y, z);
                    Block block = loc.getBlock();
                    if (isSurface) {
                        block.setType(Material.BARRIER);
                        cageBlocks.add(loc);
                        barrierBlocks.add(loc);
                    } else {
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        // 每个玩家都共享同一份方块集合引用
        for (Player player : players) {
            playerCages.put(player.getUniqueId(), cageBlocks);
        }

        int delayTicks = plugin.getConfig().getInt("hunter_removeCage", 25) * 20;

        org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                groupTaskIds.remove(getTaskId());
                // 移除方块
                cageBlocks.forEach(loc -> {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.BARRIER) {
                        block.setType(Material.AIR);
                    }
                    barrierBlocks.remove(loc);
                });
                // 清除所有玩家的笼子记录
                for (Player player : players) {
                    playerCages.remove(player.getUniqueId());
                }
                // 广播开始标题
                for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                    online.sendTitle(
                            plugin.getMessage("cage_start_title", "&a开始！"),
                            plugin.getMessage("cage_start_subtitle", ""),
                            5, 40, 15
                    );
                }
            }
        }.runTaskLater(plugin, delayTicks);
        groupTaskIds.add(task.getTaskId());
    }



    public void cleanup() {
        for (Integer taskId : taskIds.values()) {
            org.bukkit.Bukkit.getScheduler().cancelTask(taskId);
        }
        taskIds.clear();

        for (Integer taskId : groupTaskIds) {
            org.bukkit.Bukkit.getScheduler().cancelTask(taskId);
        }
        groupTaskIds.clear();

        for (Location loc : new HashSet<>(barrierBlocks)) {
            Block block = loc.getBlock();
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR);
            }
        }
        barrierBlocks.clear();
        playerCages.clear();
    }
}


