package com.huntergame.game;

import com.huntergame.HunterGame;
import com.huntergame.combat.LastDamageTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;

public class GameSettlement implements Listener {

    // 记录击杀数
    private final Map<UUID, Integer> killsMap = new HashMap<>();
    // 记录总伤害
    private final Map<UUID, Double> damageMap = new HashMap<>();
    // 记录死亡次数
    private final Map<UUID, Integer> deathsMap = new HashMap<>();
    private final HunterGame plugin;

    public GameSettlement(HunterGame plugin) {
        this.plugin = plugin;
    }

    /** 清空统计数据 */
    public void resetStats() {
        killsMap.clear();
        damageMap.clear();
        deathsMap.clear();
    }

    public Map<UUID, Integer> getKillsMap() {
        return killsMap;
    }
    public Map<UUID, Double> getDamageMap() {
        return damageMap;
    }
    public Map<UUID, Integer> getDeathsMap() {
        return deathsMap;
    }

    /** 记录玩家造成的伤害（包括直接攻击和远程武器） */
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // 只处理玩家受到的伤害
        if (!(event.getEntity() instanceof Player target)) return;

        // 获取真正的伤害源头（处理远程武器/投射物）
        Player realDamager = plugin.getLastDamageTracker().getPlayerDamager(event.getDamager());
        if (realDamager == null) return;

        // 记录伤害
        UUID damagerId = realDamager.getUniqueId();
        double damage = event.getFinalDamage();
        damageMap.put(damagerId, damageMap.getOrDefault(damagerId, 0.0) + damage);
    }

    /** 记录击杀和死亡 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();
        deathsMap.put(victimId, deathsMap.getOrDefault(victimId, 0) + 1);

        LastDamageTracker.DamageCredit killer = plugin.getLastDamageTracker().getCreditedKiller(victim);
        if (killer != null) {
            recordKill(killer.playerId());
        }
    }

    /** 记录击杀 */
    private void recordKill(UUID killerId) {
        killsMap.put(killerId, killsMap.getOrDefault(killerId, 0) + 1);
    }

    /** 游戏结束结算并显示排行榜 */
    public void showGameEndStats() {
        // 统计所有参与过的玩家
        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(killsMap.keySet());
        allPlayers.addAll(damageMap.keySet());
        allPlayers.addAll(deathsMap.keySet());

        class PlayerStats {
            String name;
            int kills;
            double damage;
            int deaths;

            PlayerStats(String name, int kills, double damage, int deaths) {
                this.name = name;
                this.kills = kills;
                this.damage = damage;
                this.deaths = deaths;
            }
        }

        List<PlayerStats> statsList = new ArrayList<>();
        for (UUID uuid : allPlayers) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            int kills = killsMap.getOrDefault(uuid, 0);
            double damage = damageMap.getOrDefault(uuid, 0.0);
            int deaths = deathsMap.getOrDefault(uuid, 0);
            statsList.add(new PlayerStats(name, kills, damage, deaths));
        }

        // 排序：击杀多 → 伤害高 → 死亡少
        statsList.sort((a, b) -> {
            if (b.kills != a.kills) return Integer.compare(b.kills, a.kills);
            if (Double.compare(b.damage, a.damage) != 0) return Double.compare(b.damage, a.damage);
            return Integer.compare(a.deaths, b.deaths);
        });

        // 输出
        Bukkit.broadcastMessage(plugin.getMessage("game_settlement_header", "&6&m---------------&e 游戏结算 &6&m---------------"));
        int rank = 1;
        for (PlayerStats ps : statsList) {
            Bukkit.broadcastMessage(plugin.getMessage("game_settlement_row", "&e%rank%.&a%player% &7| &f击杀: &c%kills% &7| &f伤害: &c%damage% &7| &f死亡: &c%deaths%")
                    .replace("%rank%", String.valueOf(rank++))
                    .replace("%player%", ps.name == null ? "Unknown" : ps.name)
                    .replace("%kills%", String.valueOf(ps.kills))
                    .replace("%damage%", String.format(Locale.US, "%.1f", ps.damage))
                    .replace("%deaths%", String.valueOf(ps.deaths)));
        }


        Bukkit.broadcastMessage(plugin.getMessage("game_settlement_footer", "&6&m---------------&e 游戏结算 &6&m---------------"));
    }
}

