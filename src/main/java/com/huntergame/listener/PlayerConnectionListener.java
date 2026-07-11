package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.game.EscaperQuitCountdown;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

public class PlayerConnectionListener implements Listener {
    private final HunterGame plugin;
    private final EscaperQuitCountdown escaperQuitCountdown;
    private final ServerSelectorListener serverSelectorListener;

    public PlayerConnectionListener(
            HunterGame plugin,
            EscaperQuitCountdown escaperQuitCountdown,
            ServerSelectorListener serverSelectorListener
    ) {
        this.plugin = plugin;
        this.escaperQuitCountdown = escaperQuitCountdown;
        this.serverSelectorListener = serverSelectorListener;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin.isResetting()) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    plugin.getMessage("login_resetting_kick", "&c服务器正在重置中，请稍后再加入！")
            );
            return;
        }

        if (plugin.isServerClosing()) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    plugin.getMessage("login_server_closing_kick", "&c游戏已结束，正在准备重置！")
            );
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location lobbyLocation = plugin.getLobbySpawnLocation();

        if (escaperQuitCountdown.isScheduled() && plugin.isEscaper(playerId)) {
            escaperQuitCountdown.cancel();
        }

        if (!plugin.isGameRunning()) {
            resetWaitingPlayer(player);

            if (lobbyLocation != null) {
                player.teleport(lobbyLocation);
                player.setGameMode(GameMode.ADVENTURE);
                player.sendMessage(plugin.getMessage("welcome_message", "&a欢迎来到猎人游戏！"));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.getInventory().clear();
                    serverSelectorListener.giveServerSelector(player);
                    plugin.getGuideManager().giveTriggerItem(player);
                    plugin.getPermissionRecipeManager().giveWaitingRecipeBook(player);
                }, 5L);
            } else {
                player.sendMessage(plugin.getMessage("lobby_not_set", "&c大厅位置未正确设置，请联系管理员！"));
            }

            event.setJoinMessage(
                    plugin.getMessage("player_join", "&a&l[+] &f%player% (&e%online%&a/%max%)")
                            .replace("%player%", player.getName())
                            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                            .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
            );
            return;
        }

        event.setJoinMessage(plugin.getMessage("player_join_start", "&a&l[+] &e%player%").replace("%player%", player.getName()));
        if (plugin.isHunter(playerId)) {
            plugin.getHunterTracker().startTrackingHunter(player);
            plugin.giveSharedBackpack(player, true);
            plugin.getPermissionRecipeManager().giveUnlockedRecipeBook(player);
        } else if (plugin.isEscaper(playerId)) {
            plugin.getHunterTracker().startTrackingEscaper(player);
            plugin.giveSharedBackpack(player, false);
            plugin.getPermissionRecipeManager().giveUnlockedRecipeBook(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        plugin.getDataStorageManager().addRank(playerId, player);
        boolean isRespawning = plugin.getStartGameCommand() != null
                && plugin.getStartGameCommand().isPlayerRespawning(playerId);
        if (!isRespawning) {
            plugin.removeRealSpectator(playerId);
        }

        if (plugin.getStartGameCommand() != null && plugin.getStartGameCommand().getVoteSystem() != null) {
            plugin.getStartGameCommand().getVoteSystem().clearPlayerVote(playerId);
        }

        if (plugin.isEscaper(playerId) && plugin.getEscapers().size() == 1) {
            escaperQuitCountdown.start();
        }

        if (plugin.isEscaper(playerId)) {
            plugin.removeEscaper(playerId);
        }
        if (plugin.isHunter(playerId) && !isRespawning) {
            plugin.removeHunter(playerId);
        }

        if (!plugin.isGameRunning()) {
            event.setQuitMessage(
                    plugin.getMessage("player_quit", "&c&l[-] &e%player% (&e%online%&c/%max%)")
                            .replace("%player%", player.getName())
                            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size() - 1))
                            .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
            );
            return;
        }

        event.setQuitMessage(plugin.getMessage("player_quit_start", "&c&l[-] &e%player%").replace("%player%", player.getName()));
    }

    private void resetWaitingPlayer(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20);
        }
    }
}
