package com.huntergame.message;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class MessageBroadcaster {

    private final JavaPlugin plugin;
    private final List<String> messages = new ArrayList<>();
    private int currentIndex = 0;
    private BukkitRunnable broadcastTask;
    private int messageInterval = 240; // 默认间隔（秒）

    public MessageBroadcaster(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig(); // 从配置文件加载
        if (isEnabled()) {
            startBroadcasting();
        }
    }

    // 加载配置文件
    public void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        // 是否启用
        if (!config.getBoolean("broadcast.enable", true)) {
            messages.clear();
            if (broadcastTask != null) broadcastTask.cancel();
            return;
        }

        // 间隔时间
        messageInterval = config.getInt("broadcast.interval", 240);
        if (messageInterval < 10) messageInterval = 10; // 防止过短

        // 消息列表（自动转换 & -> §）
        messages.clear();
        List<String> cfgMessages = config.getStringList("broadcast.messages");
        if (cfgMessages.isEmpty()) {
            messages.add(colorize("&a欢迎游玩 &c猎人游戏！"));
        } else {
            for (String msg : cfgMessages) {
                messages.add(colorize(msg));
            }
        }

        if (broadcastTask != null) {
            startBroadcasting();
        }
    }

    // 颜色代码转换
    private String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // 是否启用广播
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("broadcast.enable", true);
    }

    // 启动定时广播任务
    public void startBroadcasting() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
        }

        broadcastTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (messages.isEmpty()) {
                    cancel();
                    return;
                }
                String message = messages.get(currentIndex);
                Bukkit.broadcastMessage(message);
                currentIndex = (currentIndex + 1) % messages.size();
            }
        };

        broadcastTask.runTaskTimer(plugin, 0, messageInterval * 20L);
    }



    public void stopBroadcasting() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }
}
