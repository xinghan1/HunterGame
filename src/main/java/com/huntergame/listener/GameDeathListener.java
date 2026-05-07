package com.huntergame.listener;

import com.huntergame.HunterGame;
import com.huntergame.combat.LastDamageTracker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

public class GameDeathListener implements Listener {
    private final HunterGame plugin;

    public GameDeathListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        LastDamageTracker.DamageCredit killer = plugin.getLastDamageTracker().getCreditedKiller(player);

        if (!plugin.isGameRunning()) {
            Location lobbyLocation = plugin.getLobbyLocation();
            if (lobbyLocation != null) {
                player.teleport(lobbyLocation);
            }
            return;
        }

        if (killer != null) {
            plugin.getDataStorageManager().addKill(killer.playerId());
            plugin.getDataStorageManager().addKillput(killer.playerId(), killer.playerName());
        }
        plugin.getDataStorageManager().addDeath(playerId, player);

        if (plugin.isHunter(playerId)) {
            handleHunterDeath(player, playerId);
            return;
        }

        if (plugin.isEscaper(playerId)) {
            handleEscaperDeath(player, playerId);
        }
    }

    private void handleHunterDeath(Player player, UUID playerId) {
        if (!plugin.isFinalBattleMode()) {
            return;
        }

        if (plugin.getStartGameCommand().isPlayerRespawning(playerId)) {
            return;
        }

        boolean rewarded = plugin.getGameRewardService().giveHunterFailReward(player);
        if (rewarded) {
            player.sendTitle(
                    plugin.getMessage("hunters_defeat_title", "&c你失败了！"),
                    plugin.getMessage("hunters_defeat_subtitle", "&f终章模式死亡后无法复活"),
                    10,
                    80,
                    20
            );
        }

    }

    private void handleEscaperDeath(Player player, UUID playerId) {
        plugin.removeEscaper(playerId);
        plugin.addDeathescapers(playerId);
        plugin.addRealSpectator(playerId);
        player.setGameMode(GameMode.SPECTATOR);
        if (!plugin.isFinalBattleMode()) {
            player.sendMessage(plugin.getMessage("death_to_spectator", "&7你已经死亡，现在变成了旁观者。"));
        }

        boolean rewarded = plugin.getGameRewardService().giveEscaperFailReward(player);
        if (rewarded) {
            player.sendTitle(
                    plugin.getMessage("escapers_defeat_title", "&c你失败了！"),
                    plugin.getMessage("escapers_defeat_subtitle", "&f你已提前结算，游戏仍会继续"),
                    10,
                    80,
                    20
            );
        }

        if (plugin.getEscapers().isEmpty()) {
            endGameWithHuntersWin();
        } else {
            Bukkit.broadcastMessage(
                    plugin.getMessage("remaining_escapers", "&b逃生者还剩: %escapers% 人")
                            .replace("%escapers%", String.valueOf(plugin.getEscapers().size()))
            );
        }
    }

    private void endGameWithHuntersWin() {
        if (!plugin.beginSettlement()) {
            return;
        }

        Bukkit.broadcastMessage(plugin.getMessage("hunters_win", "&c所有逃生者已死亡！猎人胜利！"));
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID onlinePlayerId = onlinePlayer.getUniqueId();
            if (plugin.getGameRewardService().hasSettlementReward(onlinePlayerId)) {
                continue;
            }

            plugin.getDataStorageManager().saveTotalWins(onlinePlayerId, onlinePlayer);
            if (plugin.isHunter(onlinePlayerId)) {
                plugin.getDataStorageManager().addHunterWin(onlinePlayerId, onlinePlayer);
                plugin.getGameRewardService().giveHunterReward(onlinePlayer);
                onlinePlayer.sendTitle(
                        plugin.getMessage("hunters_victory_title", "&a恭喜你！"),
                        plugin.getMessage("hunters_victory_subtitle", "&f成功追杀所有逃生者"),
                        10,
                        100,
                        20
                );
            } else {
                onlinePlayer.sendTitle(
                        plugin.getMessage("game_over_title", "&6游戏结束！"),
                        plugin.getMessage("game_over_subtitle", "&c猎人获得了胜利！"),
                        10,
                        100,
                        20
                );
            }
        }
        plugin.resetGame();
    }

}
