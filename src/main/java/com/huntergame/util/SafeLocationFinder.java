package com.huntergame.util;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.function.Consumer;

import static org.bukkit.block.Biome.*;

public class SafeLocationFinder {

    private final HunterGame plugin;
    private final Random random = new Random();

    public SafeLocationFinder(HunterGame plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动异步（分片）搜索任务
     * @param world 目标世界
     * @param center 中心点
     * @param minRadius 最小搜索半径
     * @param maxRadius 最大搜索半径
     * @param callback 找到位置后的回调函数（如果超时会返回默认位置）
     */
    public void findLocation(World world, Location center, double minRadius, double maxRadius, Consumer<Location> callback) {
        new BukkitRunnable() {
            int secondsPassed = 0;
            int ticksElapsed = 0;
            double currentMinRadius = minRadius;
            double radiusStep = (maxRadius - minRadius) / 5;

            // 性能控制：每 tick 检查 3 次 (即每秒 60 次)
            final int CHECKS_PER_TICK = 3;
            final int MAX_SECONDS = 60;

            @Override
            public void run() {
                // 1. 超时检测
                if (secondsPassed >= MAX_SECONDS) {
                    Location defaultLoc = center.clone();
                    // 尝试找一个基本的最高点作为保底
                    if (world != null) {
                        int highestY = world.getHighestBlockYAt(defaultLoc);
                        defaultLoc.setY(highestY + 1);
                    }
                    Bukkit.broadcastMessage(plugin.getMessage("location_timeout", "&c搜寻超时，使用默认坐标！"));
                    callback.accept(defaultLoc);
                    this.cancel();
                    return;
                }

                // 2. 每秒发送一次 Title 提示
                if (ticksElapsed % 20 == 0) {
                    String title = plugin.getMessage("finding_location_title", "&e正在寻找安全位置...");
                    String subtitle = plugin.getMessage("finding_location_subtitle", "&f剩余时间: %time%s")
                            .replace("%time%", String.valueOf(MAX_SECONDS - secondsPassed));

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(title, subtitle, 0, 25, 5);
                    }
                    secondsPassed++;
                }

                // 3. 执行分片计算
                for (int i = 0; i < CHECKS_PER_TICK; i++) {
                    // 动态调整半径
                    if (currentMinRadius > maxRadius) {
                        currentMinRadius = minRadius; // 重置半径循环搜索
                    }
                    double currentMaxRadius = Math.min(currentMinRadius + radiusStep, maxRadius);

                    // 随机取点
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double radius = currentMinRadius + random.nextDouble() * (currentMaxRadius - currentMinRadius);

                    int x = center.getBlockX() + (int) (Math.cos(angle) * radius);
                    int z = center.getBlockZ() + (int) (Math.sin(angle) * radius);

                    // 校验并返回
                    if (isValidCoordinate(x, z)) {
                        int y = world.getHighestBlockYAt(x, z);
                        Location loc = new Location(world, x, y, z);

                        if (isSafeLocation(loc)) {
                            callback.accept(loc.add(0, 1, 0)); // 提升1格防止卡脚
                            this.cancel();
                            return;
                        }
                    }
                }

                // 如果本轮没找到，稍微扩大一点点基础半径或者什么都不做依赖随机
                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 生成一个备用位置（当常规搜索找不到时强制生成）
     */
    public Location getFallbackLocation(World world, Location center, double minDistance) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = minDistance + random.nextDouble() * 20;

        double x = center.getX() + distance * Math.cos(angle);
        double z = center.getZ() + distance * Math.sin(angle);
        int highestY = world.getHighestBlockYAt((int) x, (int) z);

        return new Location(world, x, highestY + 1, z);
    }

    // --- 私有辅助方法 ---

    private boolean isValidCoordinate(int x, int z) {
        int borderSize = 30000000;
        return x >= -borderSize && x <= borderSize && z >= -borderSize && z <= borderSize;
    }

    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        Block below = world.getBlockAt(x, y - 1, z);
        Block block = world.getBlockAt(x, y, z);
        Block above = world.getBlockAt(x, y + 1, z);

        return below.getType().isSolid()
                && !block.isLiquid()
                && !above.isLiquid()
                && y > 0
                && !isBadlandsBiome(world.getBiome(x, y, z));
    }

    private boolean isBadlandsBiome(Biome biome) {
        return biome.equals(BADLANDS) || biome.equals(WOODED_BADLANDS) || biome.equals(ERODED_BADLANDS)
                || biome.equals(FROZEN_OCEAN) || biome.equals(DEEP_COLD_OCEAN) || biome.equals(LUKEWARM_OCEAN)
                || biome.equals(DEEP_LUKEWARM_OCEAN) || biome.equals(WARM_OCEAN) || biome.equals(COLD_OCEAN)
                || biome.equals(BEACH) || biome.equals(SNOWY_BEACH) || biome.equals(OCEAN)
                || biome.equals(SNOWY_SLOPES) || biome.equals(SNOWY_TAIGA) || biome.equals(DESERT);
    }
}