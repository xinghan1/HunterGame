package com.huntergame.listener;

import com.huntergame.HunterGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

public class DragonFightListener implements Listener {
    private final HunterGame plugin;

    public DragonFightListener(HunterGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnderDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        if (plugin.isHunter(attacker.getUniqueId())) {
            event.setDamage(0.0);
        }
    }

    @EventHandler
    public void onEnderDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }
        if (!plugin.beginSettlement()) {
            return;
        }

        Bukkit.broadcastMessage(plugin.getMessage("escaper_win", "&c末影龙已被逃生者击败！逃生者胜利！"));

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (plugin.getGameRewardService().hasSettlementReward(uuid)) {
                continue;
            }

            if (plugin.getEscapers().contains(player) || plugin.isDeathescapers(uuid)) {
                plugin.getDataStorageManager().addEscapeWin(uuid, player);
                plugin.getGameRewardService().giveEscaperReward(player);
            }

            plugin.getDataStorageManager().saveTotalWins(uuid, player);

            if (plugin.isHunter(uuid)) {
                player.sendTitle(
                        plugin.getMessage("hunters_defeat_title_2", "&c你失败了！"),
                        plugin.getMessage("hunters_defeat_subtitle_2", "&f猎人未能阻止逃生者..."),
                        10,
                        100,
                        20
                );
                plugin.getGameRewardService().giveHunterFailReward(player);
            } else if (plugin.isEscaper(uuid)) {
                player.sendTitle(
                        plugin.getMessage("escapers_victory_title_2", "&a恭喜你！"),
                        plugin.getMessage("escapers_victory_subtitle_2", "&f成功逃脱猎人的追杀"),
                        10,
                        100,
                        20
                );
            } else {
                player.sendTitle(
                        plugin.getMessage("watch_victory_title_2", "&a游戏结束！"),
                        plugin.getMessage("watch_victory_subtitle_2", "&f逃生者获得了胜利！"),
                        10,
                        100,
                        20
                );
            }
        }

        plugin.resetGame();
    }

    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = getPlayerDamager(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        if (plugin.isPvpLocked()) {
            event.setCancelled(true);
            return;
        }

        if (plugin.glassCageManager.isInCage(attacker) || plugin.glassCageManager.isInCage(victim)) {
            event.setCancelled(true);
            return;
        }

        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        boolean sameHunterTeam = plugin.isHunter(attackerId) && plugin.isHunter(victimId);
        boolean sameEscaperTeam = plugin.isEscaper(attackerId) && plugin.isEscaper(victimId);
        if (sameHunterTeam || sameEscaperTeam) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getMessage("team-damage", "&c你不能攻击你的队友！"));
        }
    }

    private Player getPlayerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }
}
