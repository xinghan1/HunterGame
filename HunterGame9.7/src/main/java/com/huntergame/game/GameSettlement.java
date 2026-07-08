package com.huntergame.game;

import com.huntergame.HunterGame;
import com.huntergame.combat.LastDamageTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class GameSettlement implements Listener {
   private final Map<UUID, Integer> killsMap = new HashMap();
   private final Map<UUID, Double> damageMap = new HashMap();
   private final Map<UUID, Integer> deathsMap = new HashMap();
   private final HunterGame plugin;

   public GameSettlement(HunterGame plugin) {
      this.plugin = plugin;
   }

   public void resetStats() {
      this.killsMap.clear();
      this.damageMap.clear();
      this.deathsMap.clear();
   }

   public Map<UUID, Integer> getKillsMap() {
      return this.killsMap;
   }

   public Map<UUID, Double> getDamageMap() {
      return this.damageMap;
   }

   public Map<UUID, Integer> getDeathsMap() {
      return this.deathsMap;
   }

   @EventHandler
   public void onPlayerDamage(EntityDamageByEntityEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof Player target) {
         Player realDamager = this.plugin.getLastDamageTracker().getPlayerDamager(event.getDamager());
         if (realDamager != null) {
            UUID damagerId = realDamager.getUniqueId();
            double damage = event.getFinalDamage();
            this.damageMap.put(damagerId, (Double)this.damageMap.getOrDefault(damagerId, (double)0.0F) + damage);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player victim = event.getEntity();
      UUID victimId = victim.getUniqueId();
      this.deathsMap.put(victimId, (Integer)this.deathsMap.getOrDefault(victimId, 0) + 1);
      LastDamageTracker.DamageCredit killer = this.plugin.getLastDamageTracker().getCreditedKiller(victim);
      if (killer != null) {
         this.recordKill(killer.playerId());
      }

   }

   private void recordKill(UUID killerId) {
      this.killsMap.put(killerId, (Integer)this.killsMap.getOrDefault(killerId, 0) + 1);
   }

   public void showGameEndStats() {
      Set<UUID> allPlayers = new HashSet();
      allPlayers.addAll(this.killsMap.keySet());
      allPlayers.addAll(this.damageMap.keySet());
      allPlayers.addAll(this.deathsMap.keySet());

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

      List<PlayerStats> statsList = new ArrayList();

      for(UUID uuid : allPlayers) {
         String name = Bukkit.getOfflinePlayer(uuid).getName();
         int kills = (Integer)this.killsMap.getOrDefault(uuid, 0);
         double damage = (Double)this.damageMap.getOrDefault(uuid, (double)0.0F);
         int deaths = (Integer)this.deathsMap.getOrDefault(uuid, 0);
         statsList.add(new PlayerStats(name, kills, damage, deaths));
      }

      statsList.sort((a, b) -> {
         if (b.kills != a.kills) {
            return Integer.compare(b.kills, a.kills);
         } else {
            return Double.compare(b.damage, a.damage) != 0 ? Double.compare(b.damage, a.damage) : Integer.compare(a.deaths, b.deaths);
         }
      });
      Bukkit.broadcastMessage(this.plugin.getMessage("game_settlement_header", "&6&m---------------&e \u6e38\u620f\u7ed3\u7b97 &6&m---------------"));
      int rank = 1;

      for(PlayerStats ps : statsList) {
         Bukkit.broadcastMessage(this.plugin.getMessage("game_settlement_row", "&e%rank%.&a%player% &7| &f\u51fb\u6740: &c%kills% &7| &f\u4f24\u5bb3: &c%damage% &7| &f\u6b7b\u4ea1: &c%deaths%").replace("%rank%", String.valueOf(rank++)).replace("%player%", ps.name == null ? "Unknown" : ps.name).replace("%kills%", String.valueOf(ps.kills)).replace("%damage%", String.format(Locale.US, "%.1f", ps.damage)).replace("%deaths%", String.valueOf(ps.deaths)));
      }

      Bukkit.broadcastMessage(this.plugin.getMessage("game_settlement_footer", "&6&m---------------&e \u6e38\u620f\u7ed3\u7b97 &6&m---------------"));
   }
}
