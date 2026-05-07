package com.huntergame.game;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class EscaperQuitCountdown {
    private final HunterGame plugin;
    private int taskId = -1;
    private int secondsLeft;
    private boolean scheduled;

    public EscaperQuitCountdown(HunterGame plugin) {
        this.plugin = plugin;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    public void start() {
        if (scheduled) {
            return;
        }

        scheduled = true;
        FileConfiguration config = plugin.getConfig();
        secondsLeft = config.getInt("game.escapers_quit_countdown", 120);

        Bukkit.broadcastMessage(plugin.getMessage("escaper_quit_countdown_started", "&c所有逃生者已退出，%seconds%秒后猎人胜利！")
                .replace("%seconds%", String.valueOf(secondsLeft)));

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            secondsLeft--;

            if (secondsLeft <= 0) {
                Bukkit.getScheduler().cancelTask(taskId);
                taskId = -1;
                scheduled = false;

                if (!plugin.beginSettlement()) {
                    return;
                }

                for (Player hunter : plugin.getHunters()) {
                    if (plugin.getGameRewardService().hasSettlementReward(hunter.getUniqueId())) {
                        continue;
                    }

                    plugin.getDataStorageManager().addHunterWin(hunter.getUniqueId(), hunter);
                    plugin.getGameRewardService().giveHunterReward(hunter);
                }

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameRewardService().hasSettlementReward(onlinePlayer.getUniqueId())) {
                        continue;
                    }

                    plugin.getDataStorageManager().saveTotalWins(onlinePlayer.getUniqueId(), onlinePlayer);
                    onlinePlayer.sendTitle(
                            plugin.getMessage("hunters_victory_title", "&6恭喜！"),
                            plugin.getMessage("hunters_victory_subtitle", "&c猎人胜利！"),
                            10,
                            70,
                            20
                    );
                }

                plugin.resetGame();
                return;
            }

            if (secondsLeft % 5 == 0 || secondsLeft <= 5) {
                Bukkit.broadcastMessage(plugin.getMessage("escaper_quit_countdown_remaining", "&e剩余时间: %seconds%秒")
                        .replace("%seconds%", String.valueOf(secondsLeft)));
            }
        }, 20L, 20L);
    }

    public void cancel() {
        cancelTask();
        scheduled = false;
        Bukkit.broadcastMessage(plugin.getMessage("escaper_quit_countdown_cancelled", "&a逃生者已回归，取消倒计时！"));
    }

    public void cancelSilently() {
        cancelTask();
        scheduled = false;
    }

    private void cancelTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
