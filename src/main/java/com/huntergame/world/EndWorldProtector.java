package com.huntergame.world;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class EndWorldProtector {
    private final HunterGame plugin;
    private BukkitRunnable protectionTask;
    private final Set<UUID> protectedWorlds = new HashSet<>();

    // 末地出生点坐标 (默认黑曜石平台中心)
    private final int PROTECT_X = 0;
    private final int PROTECT_Y = 70;
    private final int PROTECT_Z = 0;
    private final int PROTECT_RADIUS = 1; // 3x3区域 (半径1)

    public EndWorldProtector(HunterGame plugin) {
        this.plugin = plugin;

        // 启动保护任务
        startProtectionTask();
    }

    // 启动出生点保护任务
    private void startProtectionTask() {
        // 先停止可能已存在的任务
        stopProtectionTask();

        // 每秒检查并保护出生点区域
        protectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) return;

                // 只保护末地
                World endWorld = Bukkit.getWorld("world_the_end");
                if (endWorld == null) return;

                protectEndSpawn(endWorld);
            }
        };

        protectionTask.runTaskTimer(plugin, 0L, 20L * 5); // 每5秒执行一次，避免无意义地每秒扫方块
    }

    // 停止保护任务
    public void stopProtectionTask() {
        if (protectionTask != null) {
            protectionTask.cancel();
            protectionTask = null;
        }
    }

    // 保护末地出生点区域
    private void protectEndSpawn(World world) {
        // 标记世界为受保护
        protectedWorlds.add(world.getUID());

        // 3x3区域保护 (X, Y, Z 各±1)
        for (int x = PROTECT_X - PROTECT_RADIUS; x <= PROTECT_X + PROTECT_RADIUS; x++) {
            for (int y = PROTECT_Y - PROTECT_RADIUS; y <= PROTECT_Y + PROTECT_RADIUS; y++) {
                for (int z = PROTECT_Z - PROTECT_RADIUS; z <= PROTECT_Z + PROTECT_RADIUS; z++) {
                    Block block = world.getBlockAt(x, y, z);

                    // 只将非空气方块设置为空气
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR);
                    }

                }
            }
        }
    }




    // 游戏结束时清理
    public void cleanup() {
        stopProtectionTask();
        protectedWorlds.clear();
    }
}
