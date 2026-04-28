package com.huntergame.world;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;

public class EndermanLimiter implements Listener {
    private final HunterGame plugin;
    private final int endermanLimit;
    // 配置：检查间隔（ ticks，20ticks=1秒，默认10秒）
    private final int checkInterval;
    private BukkitTask checkTask;

    public EndermanLimiter(HunterGame plugin){
        this.plugin = plugin;
        this.endermanLimit = plugin.getConfig().getInt("game.enderman_limit", 10);
        this.checkInterval = Math.max(20 * 5, plugin.getConfig().getInt("game.enderman_check_interval", 20 * 10));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startEndermanCheckTask();
    }

    /**
     * 监听生物生成事件，阻止末地超过数量限制的末影人生成
     */
    @EventHandler
    public void onEndermanSpawn(CreatureSpawnEvent event) {
        // 只处理末影人，且在末地维度
        if (event.getEntity() instanceof Enderman &&
                event.getLocation().getWorld().getEnvironment() == World.Environment.THE_END) {

            // 计算当前末地的末影人总数
            int currentCount = countEndermenInEnd(event.getLocation().getWorld());

            // 如果已达上限，取消生成
            if (currentCount >= endermanLimit) {
                event.setCancelled(true);
                // 可选：调试日志
                // getLogger().info("阻止末影人生成，当前数量：" + currentCount);
            }
        }
    }

    /**
     * 启动定时任务，检查并清除末地超过限制的末影人
     */
    private void startEndermanCheckTask() {
        stop();
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) {
                    return;
                }
                // 遍历所有末地世界（可能有多个末地维度）
                for (World world : Bukkit.getWorlds()) {
                    if (world.getEnvironment() == World.Environment.THE_END) {
                        int currentCount = countEndermenInEnd(world);
                        // 如果超过上限，清除多余的末影人
                        if (currentCount > endermanLimit) {
                            removeExcessEndermen(world, currentCount - endermanLimit);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, checkInterval); // 立即开始，每checkInterval ticks执行一次
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /**
     * 计算指定末地世界的末影人数量
     */
    private int countEndermenInEnd(World endWorld) {
        return endWorld.getEntitiesByClass(Enderman.class).size();
    }

    /**
     * 清除末地世界中多余的末影人
     * @param endWorld 末地世界
     * @param excess 超过限制的数量
     */
    private void removeExcessEndermen(World endWorld, int excess) {
        int removed = 0;
        // 遍历末影人，清除指定数量的多余实体
        Collection<Enderman> endermen = endWorld.getEntitiesByClass(Enderman.class);
        for (Enderman enderman : endermen) {
            if (removed >= excess) break;
            enderman.remove();
            removed++;
        }
        // 可选：输出清除日志
        if (removed > 0) {
            plugin.getLogger().info("末地清除了 " + removed + " 只末影人，当前数量：" + (countEndermenInEnd(endWorld)));
        }
    }
}
