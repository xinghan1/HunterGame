package com.huntergame;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

public class GameRewards implements Listener {
    private final HunterGame plugin;
    private BukkitTask timedRewardTask;

    public GameRewards(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerKill(EntityDeathEvent event) {
        if (!plugin.isGameRunning()) {
            return;
        }

        // 如果死亡的玩家是逃生者或猎人
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity(); // 死亡的玩家
            Player killer = event.getEntity().getKiller(); // 杀人的玩家
            if (killer != null) {
                UUID playerId = player.getUniqueId();
                UUID killeruuid = killer.getUniqueId();

                // 如果是猎人击杀逃生者
                if (plugin.isHunter(killeruuid) && plugin.isEscaper(playerId)) {
                    // 获取猎人击杀逃生者的奖励指令
                    List<String> killCommands = plugin.getConfig().getStringList("rewards.hunter_kill");
                    // 执行每个指令
                    dispatchCommands(killCommands, killer);
                }

                // 如果是逃生者击杀猎人
                if (plugin.isEscaper(killeruuid) && plugin.isHunter(playerId)) {
                    // 获取逃生者击杀猎人的奖励指令
                    List<String> escaperKillCommands = plugin.getConfig().getStringList("rewards.escaper_kill");

                    // 执行每个指令
                    dispatchCommands(escaperKillCommands, killer);
                }
            }
        }
    }

    // 猎人完成任务后奖励
    public void giveHunterReward(Player player) {
        if (plugin.isFinalBattleMode()) {
            // 执行终章之战特有的逻辑
            List<String> hunterCommands2 = plugin.getConfig().getStringList("rewards.hunter_final_reward");
            dispatchCommands(hunterCommands2, player);
            return;
        }

        List<String> hunterCommands = plugin.getConfig().getStringList("rewards.hunter_reward");
        dispatchCommands(hunterCommands, player);
    }

    // 猎人完成任务后奖励
    public void giveHunterFailReward(Player player) {
        if (plugin.isFinalBattleMode()) {
            List<String> hunterCommands2 = plugin.getConfig().getStringList("rewards.hunter_final_fail_reward");
            dispatchCommands(hunterCommands2, player);
            return;
        }

        List<String> hunterCommands = plugin.getConfig().getStringList("rewards.hunter_fail_reward");
        dispatchCommands(hunterCommands, player);
    }

    // 逃生者完成任务后奖励
    public void giveEscaperReward(Player player) {
        if (plugin.isFinalBattleMode()) {
            List<String> escaperCommands2 = plugin.getConfig().getStringList("rewards.escaper_final_reward");
            dispatchCommands(escaperCommands2, player);
            return;
        }

        List<String> escaperCommands = plugin.getConfig().getStringList("rewards.escaper_reward");
        dispatchCommands(escaperCommands, player);
    }

    // 逃生者完成任务后奖励
    public void giveEscaperFailReward(Player player) {
        if (plugin.isFinalBattleMode()) {
            List<String> escaperCommands2 = plugin.getConfig().getStringList("rewards.escaper_final_fail_reward");
            dispatchCommands(escaperCommands2, player);
            return;
        }

        List<String> escaperCommands = plugin.getConfig().getStringList("rewards.escaper_fail_reward");
        dispatchCommands(escaperCommands, player);
    }

    // 定时奖励
    public void startGameRewardTask() {
        stopGameRewardTask();
        boolean enabled = plugin.getConfig().getBoolean("timed_rewards.enabled", false);
        if (!enabled) {
            return; // 如果定时奖励没有启用，退出
        }

        int interval = plugin.getConfig().getInt("timed_rewards.interval", 300);
        List<String> rewardCommands = plugin.getConfig().getStringList("timed_rewards.reward_commands");
        String rewardMessage = plugin.getConfig().getString("timed_rewards.reward_message", "&e你收到了定时奖励！");

        if (rewardCommands.isEmpty()) {
            plugin.getLogger().warning("定时奖励指令未设置！");
            return;
        }

        timedRewardTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 获取所有在线玩家，并对每个玩家执行定时奖励指令
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 排除创造模式和旁观者模式
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
                    // 执行每个指令
                    dispatchCommands(rewardCommands, player);

                    // 发送定时奖励消息
                    String finalMessage = ChatColor.translateAlternateColorCodes('&', rewardMessage).replace("%player%", player.getName());
                    player.sendMessage(finalMessage);
                }
            }
        }.runTaskTimer(plugin, 0L, interval * 20L);
    }

    public void stopGameRewardTask() {
        if (timedRewardTask != null) {
            timedRewardTask.cancel();
            timedRewardTask = null;
        }
    }

    private void dispatchCommands(List<String> commands, Player player) {
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        }
    }
}
