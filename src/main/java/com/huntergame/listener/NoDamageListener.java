package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class NoDamageListener implements Listener {
    private final HunterGame plugin;

    public NoDamageListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.isGameRunning()) {
            event.setCancelled(true); // 取消伤害
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isGameRunning()) {
            event.setCancelled(true); // 游戏未开始禁止破坏方块
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isGameRunning()) {
            event.setCancelled(true); // 游戏未开始禁止放置方块
        }
    }
}
