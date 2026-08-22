package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class WaitingLobbyListener implements Listener {
    private final HunterGame plugin;

    public WaitingLobbyListener(HunterGame plugin) {
        this.plugin = plugin;
        // 处理插件启用前或区块重新加载时已经存在的生物实体。
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::removeWaitingLivingEntities, 1L, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWaitingCreatureSpawn(CreatureSpawnEvent event) {
        if (plugin.isWaitingLobbyWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWaitingPlayerMove(PlayerMoveEvent event) {
        if (plugin.isGameRunning() || plugin.isServerClosing()) {
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }

        if (to.getY() <= to.getWorld().getMinHeight()) {
            teleportWaitingPlayerToLobby(event.getPlayer());
        }
    }

    @EventHandler
    public void onWaitingVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || plugin.isGameRunning() || plugin.isServerClosing()) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }

        event.setCancelled(true);
        teleportWaitingPlayerToLobby((Player) event.getEntity());
    }

    private void teleportWaitingPlayerToLobby(Player player) {
        Location lobbyLocation = plugin.getLobbySpawnLocation();
        if (lobbyLocation == null) {
            player.sendMessage(plugin.getMessage("lobby_not_set", "&c大厅位置未正确设置，请联系管理员！"));
            return;
        }

        player.setFallDistance(0);
        player.teleport(lobbyLocation);
    }

    private void removeWaitingLivingEntities() {
        World lobbyWorld = plugin.getLobbyWorld();
        if (!plugin.isWaitingLobbyWorld(lobbyWorld)) {
            return;
        }

        for (LivingEntity entity : lobbyWorld.getLivingEntities()) {
            if (!(entity instanceof Player) && !(entity instanceof ArmorStand)) {
                entity.remove();
            }
        }
    }
}
