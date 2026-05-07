package com.huntergame.motd;

import com.huntergame.HunterGame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class MotdListener implements Listener {
    private final HunterGame plugin;

    public MotdListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        // 获取当前状态对应的MOTD
        String motd = getCurrentMOTD();
        // 设置MOTD（支持颜色代码）
        event.setMotd(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', motd));
    }

    // 获取当前状态对应的MOTD
    public String getCurrentMOTD() {
        if (plugin.isResetting()) {
            return plugin.getMessage("motd.resetting", "&c重置中");
        } else if (plugin.isGameRunning()) {
            return plugin.getMessage("motd.game-running", "&c游戏中");
        } else if (plugin.isGameEnded()) {
            return plugin.getMessage("motd.ended", "&6已结束");
        } else {
            return plugin.getMessage("motd.waiting", "&a等待中");
        }
    }

}

