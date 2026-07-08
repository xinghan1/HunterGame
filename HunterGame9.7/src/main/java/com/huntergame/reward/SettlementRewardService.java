package com.huntergame.reward;

import com.huntergame.HunterGame;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class SettlementRewardService {
   private static final String ROOT = "settlement_rewards";
   private final HunterGame plugin;
   private final Set<UUID> rewardedPlayers = new HashSet();

   public SettlementRewardService(HunterGame plugin) {
      this.plugin = plugin;
   }

   public void resetForGame() {
      this.rewardedPlayers.clear();
   }

   public boolean rewardHunter(Player player, boolean win) {
      return this.reward(player, "hunter", win);
   }

   public boolean rewardEscaper(Player player, boolean win) {
      return this.reward(player, "escaper", win);
   }

   public boolean hasRewarded(UUID uuid) {
      return this.rewardedPlayers.contains(uuid);
   }

   private boolean reward(Player player, String role, boolean win) {
      if (player != null && player.isOnline() && this.plugin.getConfig().getBoolean("settlement_rewards.enabled", true)) {
         UUID uuid = player.getUniqueId();
         if (this.isEligible(uuid, role) && this.rewardedPlayers.add(uuid)) {
            RewardStats stats = this.readStats(uuid);
            RewardValues current = this.calculate(role, stats, win);
            double proficiency = this.calculateProficiency(player, role, stats, win);
            Map<String, String> placeholders = this.buildPlaceholders(player, role, win, stats, current, proficiency);
            this.dispatchConfiguredCommands(player, role, win, placeholders);
            this.sendRewardMessage(player, placeholders, current);
            this.addGamePlayed(player);
            this.applyProficiency(player, proficiency);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isEligible(UUID uuid, String role) {
      if ("hunter".equals(role)) {
         return this.plugin.isHunter(uuid);
      } else {
         return this.plugin.isEscaper(uuid) || this.plugin.isDeathescapers(uuid);
      }
   }

   private RewardStats readStats(UUID uuid) {
      RewardStats stats = new RewardStats();
      if (this.plugin.getGameSettlement() == null) {
         return stats;
      } else {
         stats.kills = (Integer)this.plugin.getGameSettlement().getKillsMap().getOrDefault(uuid, 0);
         stats.damage = (Double)this.plugin.getGameSettlement().getDamageMap().getOrDefault(uuid, (double)0.0F);
         stats.deaths = (Integer)this.plugin.getGameSettlement().getDeathsMap().getOrDefault(uuid, 0);
         stats.minutes = Math.max((double)0.0F, (double)this.plugin.getElapsedSeconds() / (double)60.0F);
         return stats;
      }
   }

   private RewardValues calculate(String role, RewardStats stats, boolean win) {
      String base = "settlement_rewards.formulas." + role;
      double money = (double)stats.kills * this.getDouble(base + ".money.kill", (double)0.0F) + stats.damage * this.getDouble(base + ".money.damage", (double)0.0F) + (double)stats.deaths * this.getDouble(base + ".money.death", (double)0.0F) + stats.minutes * this.getDouble(base + ".money.playtime_minute", (double)0.0F) + this.getDouble(base + ".money." + (win ? "win" : "fail"), (double)0.0F);
      double exp = (double)stats.kills * this.getDouble(base + ".exp.kill", (double)0.0F) + stats.damage * this.getDouble(base + ".exp.damage", (double)0.0F) + (double)stats.deaths * this.getDouble(base + ".exp.death", (double)0.0F) + stats.minutes * this.getDouble(base + ".exp.playtime_minute", (double)0.0F) + this.getDouble(base + ".exp." + (win ? "win" : "fail"), (double)0.0F);
      return new RewardValues(Math.max(0L, Math.round(money)), Math.max(0L, Math.round(exp)));
   }

   private double calculateProficiency(Player player, String role, RewardStats stats, boolean win) {
      return this.plugin.getRankManager() == null ? (double)0.0F : this.plugin.getRankManager().calculateSettlementProficiency(player, role, stats.kills, stats.damage, stats.deaths, win);
   }

   private Map<String, String> buildPlaceholders(Player player, String role, boolean win, RewardStats stats, RewardValues current, double proficiency) {
      Map<String, String> placeholders = new HashMap();
      this.putPlaceholder(placeholders, "player", player.getName());
      this.putPlaceholder(placeholders, "role", role);
      this.putPlaceholder(placeholders, "result", win ? "\u80dc\u5229" : "\u5931\u8d25");
      this.putPlaceholder(placeholders, "kills", String.valueOf(stats.kills));
      this.putPlaceholder(placeholders, "damage", this.formatDecimal(stats.damage));
      this.putPlaceholder(placeholders, "deaths", String.valueOf(stats.deaths));
      this.putPlaceholder(placeholders, "minutes", this.formatDecimal(stats.minutes));
      this.putPlaceholder(placeholders, "money", String.valueOf(current.money));
      this.putPlaceholder(placeholders, "exp", String.valueOf(current.exp));
      this.putPlaceholder(placeholders, "proficiency", this.formatDecimal(proficiency));
      this.putPlaceholder(placeholders, "hunter_money", "hunter".equals(role) ? String.valueOf(current.money) : "0");
      this.putPlaceholder(placeholders, "hunter_exp", "hunter".equals(role) ? String.valueOf(current.exp) : "0");
      this.putPlaceholder(placeholders, "escaper_money", "escaper".equals(role) ? String.valueOf(current.money) : "0");
      this.putPlaceholder(placeholders, "escaper_exp", "escaper".equals(role) ? String.valueOf(current.exp) : "0");
      return placeholders;
   }

   private void dispatchConfiguredCommands(Player player, String role, boolean win, Map<String, String> placeholders) {
      for(String rawCommand : this.plugin.getConfig().getStringList("settlement_rewards.commands." + role + "_" + (win ? "win" : "fail"))) {
         String command = this.applyPlaceholders(rawCommand, placeholders).trim();
         if (!command.isEmpty()) {
            if (command.startsWith("/")) {
               command = command.substring(1);
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
         }
      }

   }

   private void sendRewardMessage(Player player, Map<String, String> placeholders, RewardValues current) {
      if (this.plugin.getConfig().getBoolean("settlement_rewards.message.enabled", true)) {
         long var10000 = current.money;
         String rewards = "\u6e38\u620f\u5e01 " + var10000 + "\uff0c\u5927\u5385\u7ecf\u9a8c " + current.exp + "\uff0c\u719f\u7ec3\u5ea6 " + (String)placeholders.getOrDefault("%proficiency%", "0");
         this.putPlaceholder(placeholders, "rewards", rewards);

         for(String line : this.getRewardMessageLines()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', this.applyPlaceholders(line, placeholders)));
         }

      }
   }

   private List<String> getRewardMessageLines() {
      String path = "settlement_rewards.message.text";
      if (this.plugin.getConfig().isList(path)) {
         return this.plugin.getConfig().getStringList(path);
      } else {
         String text = this.plugin.getConfig().getString(path, "&a\u7ed3\u7b97\u5956\u52b1: &f%rewards%");
         return Collections.singletonList(text);
      }
   }

   private void applyProficiency(Player player, double proficiency) {
      if (this.plugin.getDataStorageManager() != null) {
         if (!(Math.abs(proficiency) < 1.0E-4)) {
            this.plugin.getDataStorageManager().addProficiency(player, proficiency);
         }
      }
   }

   private void addGamePlayed(Player player) {
      if (this.plugin.getDataStorageManager() != null) {
         this.plugin.getDataStorageManager().addGamePlayed(player.getUniqueId(), player);
      }
   }

   private String applyPlaceholders(String text, Map<String, String> placeholders) {
      String result = text;

      for(Map.Entry<String, String> entry : placeholders.entrySet()) {
         result = result.replace((CharSequence)entry.getKey(), (CharSequence)entry.getValue());
      }

      return result;
   }

   private void putPlaceholder(Map<String, String> placeholders, String key, String value) {
      placeholders.put("%" + key + "%", value);
      placeholders.put("{" + key + "}", value);
   }

   private double getDouble(String path, double fallback) {
      return this.plugin.getConfig().getDouble(path, fallback);
   }

   private String formatDecimal(double value) {
      return Math.rint(value) == value ? String.valueOf((long)value) : String.format(Locale.ROOT, "%.2f", value);
   }

   private static final class RewardStats {
      int kills;
      double damage;
      int deaths;
      double minutes;
   }

   private static final class RewardValues {
      final long money;
      final long exp;

      RewardValues(long money, long exp) {
         this.money = money;
         this.exp = exp;
      }
   }
}
