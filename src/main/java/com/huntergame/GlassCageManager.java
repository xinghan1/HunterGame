package com.huntergame;

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

public class GlassCageManager {
    private final HunterGame plugin;
    private final Map<UUID, Set<Location>> playerCages = new ConcurrentHashMap<>();
    private final Set<Location> barrierBlocks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> taskIds = new ConcurrentHashMap<>();

    public GlassCageManager(HunterGame plugin) {
        this.plugin = plugin;
    }

    /**
     * 在玩家周围生成空心屏障立方体
     */
    public void createCage(Player player) {
        UUID playerId = player.getUniqueId();
        Location center = player.getLocation().clone();
        Set<Location> cageBlocks = new HashSet<>();
        int radius = 2; // 半径2格，生成5x5x5立方体

        // 遍历立方体范围
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // 仅生成表面方块（空心）
                    boolean isSurface =
                            Math.abs(x) == radius ||
                                    Math.abs(y) == radius ||
                                    Math.abs(z) == radius;

                    Location loc = center.clone().add(x, y, z);
                    Block block = loc.getBlock();

                    if (isSurface) {
                        // 表面设置为屏障
                        block.setType(Material.BARRIER);
                        cageBlocks.add(loc);
                        barrierBlocks.add(loc);
                    } else {
                        // 内部设置为空气
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        playerCages.put(player.getUniqueId(), cageBlocks);

        // 根据阵营设置不同消失时间
        FileConfiguration config = plugin.getConfig();
        int delayTicks = plugin.isEscaper(playerId) ? config.getInt("escapers_removeCage", 15) * 20 : config.getInt("hunter_removeCage", 25) * 20; // 逃生者15秒，猎人25秒

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                removeCage(player);
                taskIds.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, delayTicks).getTaskId();

        taskIds.put(player.getUniqueId(), taskId);
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

        new BukkitRunnable() {
            @Override
            public void run() {
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
                    online.sendTitle("§a开始！", "", 5, 40, 15);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /**
     * 移除屏障墙
     */
    public void removeCage(Player player) {
        Set<Location> blocks = playerCages.remove(player.getUniqueId());
        if (blocks == null) return;

        blocks.forEach(loc -> {
            Block block = loc.getBlock();
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR);
            }
            barrierBlocks.remove(loc);
        });

        // 所有笼子消失后广播开始标题
        if (playerCages.isEmpty()) {
            for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                online.sendTitle("§a开始！", "", 5, 40, 15);
            }
        }
    }
}