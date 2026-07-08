package com.huntergame.rank;

import com.huntergame.HunterGame;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class RankManager {
   private final HunterGame plugin;
   private File rankFile;
   private FileConfiguration rankConfig;
   private final List<Rank> ranks = new ArrayList();
   private double GameStartReward;
   private double FinalBattle_EscaperKillReward;
   private double OrdinaryBattle_EscaperKillReward;
   private double FinalBattle_HunterKillReward;
   private double OrdinaryBattle_HunterKillReward;
   private double FinalBattle_EscaperDeathReward;
   private double OrdinaryBattle_EscaperDeathReward;
   private double FinalBattle_HunterDeathReward;
   private double OrdinaryBattle_HunterDeathReward;
   private double FinalBattle_EscaperWinReward;
   private double OrdinaryBattle_EscaperWinReward;
   private double FinalBattle_HunterFailReward;
   private double OrdinaryBattle_HunterFailReward;
   private double FinalBattle_EscaperFailReward;
   private double OrdinaryBattle_EscaperFailReward;
   private double FinalBattle_HunterWinReward;
   private double OrdinaryBattle_HunterWinReward;
   private final Map<String, List<String>> seasonRewards = new HashMap();
   private final Map<String, Double> mobTypeRewards = new HashMap();

   public RankManager(HunterGame plugin) {
      this.plugin = plugin;
      this.saveDefaultConfig();
      this.reloadConfig();
   }

   public void saveDefaultConfig() {
      this.rankFile = new File(this.plugin.getDataFolder(), "rank.yml");
      if (!this.rankFile.exists()) {
         this.plugin.saveResource("rank.yml", false);
      }

   }

   public void reloadConfig() {
      this.ranks.clear();
      this.mobTypeRewards.clear();
      this.seasonRewards.clear();
      this.rankConfig = YamlConfiguration.loadConfiguration(this.rankFile);
      ConfigurationSection ranksSection = this.rankConfig.getConfigurationSection("ranks");
      if (ranksSection != null) {
         List<Integer> keys = new ArrayList();

         for(String key : ranksSection.getKeys(false)) {
            try {
               keys.add(Integer.parseInt(key));
            } catch (NumberFormatException var11) {
               this.plugin.getLogger().warning("\u6bb5\u4f4d key \u5fc5\u987b\u662f\u6574\u6570: " + key);
            }
         }

         Collections.sort(keys);

         for(int key : keys) {
            ConfigurationSection section = ranksSection.getConfigurationSection(String.valueOf(key));
            if (section != null) {
               String name = section.getString("name", "\u672a\u547d\u540d\u6bb5\u4f4d");
               double min = section.getDouble("min", (double)0.0F);
               double max = section.getDouble("max", (double)0.0F);
               this.ranks.add(new Rank(name, min, max));
            }
         }
      }

      ConfigurationSection rewardsSection = this.rankConfig.getConfigurationSection("proficiency-rewards");
      if (rewardsSection != null) {
         this.GameStartReward = rewardsSection.getDouble("GameStartReward", 0.3);
         this.FinalBattle_EscaperKillReward = rewardsSection.getDouble("FinalBattle_EscaperKillReward", (double)0.5F);
         this.OrdinaryBattle_EscaperKillReward = rewardsSection.getDouble("OrdinaryBattle_EscaperKillReward", 0.4);
         this.FinalBattle_HunterKillReward = rewardsSection.getDouble("FinalBattle_HunterKillReward", (double)0.5F);
         this.OrdinaryBattle_HunterKillReward = rewardsSection.getDouble("OrdinaryBattle_HunterKillReward", 0.4);
         this.FinalBattle_EscaperDeathReward = rewardsSection.getDouble("FinalBattle_EscaperDeathReward", (double)-1.5F);
         this.OrdinaryBattle_EscaperDeathReward = rewardsSection.getDouble("OrdinaryBattle_EscaperDeathReward", (double)-1.0F);
         this.FinalBattle_HunterDeathReward = rewardsSection.getDouble("FinalBattle_HunterDeathReward", -0.3);
         this.OrdinaryBattle_HunterDeathReward = rewardsSection.getDouble("OrdinaryBattle_HunterDeathReward", -0.4);
         this.FinalBattle_EscaperWinReward = rewardsSection.getDouble("FinalBattle_EscaperWinReward", (double)3.0F);
         this.OrdinaryBattle_EscaperWinReward = rewardsSection.getDouble("OrdinaryBattle_EscaperWinReward", (double)8.0F);
         this.FinalBattle_EscaperFailReward = rewardsSection.getDouble("FinalBattle_EscaperFailReward", (double)-1.0F);
         this.OrdinaryBattle_EscaperFailReward = rewardsSection.getDouble("OrdinaryBattle_EscaperFailReward", (double)-2.0F);
         this.FinalBattle_HunterWinReward = rewardsSection.getDouble("FinalBattle_HunterWinReward", (double)1.0F);
         this.OrdinaryBattle_HunterWinReward = rewardsSection.getDouble("OrdinaryBattle_HunterWinReward", (double)1.5F);
         this.FinalBattle_HunterFailReward = rewardsSection.getDouble("FinalBattle_HunterFailReward", (double)-1.0F);
         this.OrdinaryBattle_HunterFailReward = rewardsSection.getDouble("OrdinaryBattle_HunterFailReward", (double)-1.5F);
      }

      ConfigurationSection seasonSection = this.rankConfig.getConfigurationSection("season-rewards");
      if (seasonSection != null) {
         for(String rankName : seasonSection.getKeys(false)) {
            List<String> commands = seasonSection.getStringList(rankName);
            this.seasonRewards.put(rankName, commands);
         }
      }

   }

   public Map<String, List<String>> getSeasonRewards() {
      return this.seasonRewards;
   }

   public String getRankName(double score) {
      for(Rank rank : this.ranks) {
         if (score >= rank.getMin() && score < rank.getMax()) {
            return rank.getName();
         }
      }

      return this.ranks.isEmpty() ? "\u65e0\u6bb5\u4f4d" : ((Rank)this.ranks.get(this.ranks.size() - 1)).getName();
   }

   public double getNextRankRequired(double score) {
      for(Rank rank : this.ranks) {
         if (score < rank.getMin()) {
            return rank.getMin();
         }
      }

      return (double)-1.0F;
   }

   public String getRank(double score) {
      for(Rank rank : this.ranks) {
         if (score >= rank.getMin() && score < rank.getMax()) {
            return rank.getName();
         }
      }

      return this.ranks.isEmpty() ? "\u65e0\u6bb5\u4f4d" : ((Rank)this.ranks.get(this.ranks.size() - 1)).getName();
   }

   public double getGameStartReward() {
      return this.GameStartReward;
   }

   public boolean isSettlementProficiencyEnabled() {
      ConfigurationSection rewardsSection = this.rankConfig.getConfigurationSection("proficiency-rewards");
      return rewardsSection != null && rewardsSection.getBoolean("enabled", true);
   }

   public double calculateSettlementProficiency(Player player, String role, int kills, double damage, int deaths, boolean win) {
      if (player != null && this.isSettlementProficiencyEnabled()) {
         String modeKey = this.plugin.isFinalBattleMode() ? "final_battle" : "ordinary_battle";
         return this.calculateSettlementProficiency(modeKey, role, kills, damage, deaths, win);
      } else {
         return (double)0.0F;
      }
   }

   public double calculateSettlementProficiency(String modeKey, String role, int kills, double damage, int deaths, boolean win) {
      ConfigurationSection rewardsSection = this.rankConfig.getConfigurationSection("proficiency-rewards");
      if (rewardsSection != null && rewardsSection.getBoolean("enabled", true)) {
         String cleanMode = modeKey == null ? "" : modeKey.trim();
         String cleanRole = role == null ? "" : role.trim();
         if (!cleanMode.isEmpty() && !cleanRole.isEmpty()) {
            double reward = (double)kills * rewardsSection.getDouble(cleanMode + "." + cleanRole + ".kill", (double)0.0F) + damage * rewardsSection.getDouble(cleanMode + "." + cleanRole + ".damage", (double)0.0F) + (double)deaths * rewardsSection.getDouble(cleanMode + "." + cleanRole + ".death", (double)0.0F) + rewardsSection.getDouble(cleanMode + "." + cleanRole + "." + (win ? "win" : "fail"), (double)0.0F);
            double multiplier = rewardsSection.getDouble(cleanMode + ".multiplier", (double)1.0F);
            return reward * multiplier;
         } else {
            return (double)0.0F;
         }
      } else {
         return (double)0.0F;
      }
   }

   public double getFinalBattle_EscaperKillReward() {
      return this.FinalBattle_EscaperKillReward;
   }

   public double getOrdinaryBattle_EscaperKillReward() {
      return this.OrdinaryBattle_EscaperKillReward;
   }

   public double getFinalBattle_HunterKillReward() {
      return this.FinalBattle_HunterKillReward;
   }

   public double getOrdinaryBattle_HunterKillReward() {
      return this.OrdinaryBattle_HunterKillReward;
   }

   public double getFinalBattle_EscaperDeathReward() {
      return this.FinalBattle_EscaperDeathReward;
   }

   public double getOrdinaryBattle_EscaperDeathReward() {
      return this.OrdinaryBattle_EscaperDeathReward;
   }

   public double getFinalBattle_HunterDeathReward() {
      return this.FinalBattle_HunterDeathReward;
   }

   public double getOrdinaryBattle_HunterDeathReward() {
      return this.OrdinaryBattle_HunterDeathReward;
   }

   public double getFinalBattle_EscaperWinReward() {
      return this.FinalBattle_EscaperWinReward;
   }

   public double getOrdinaryBattle_EscaperWinReward() {
      return this.OrdinaryBattle_EscaperWinReward;
   }

   public double getFinalBattle_EscaperFailReward() {
      return this.FinalBattle_EscaperFailReward;
   }

   public double getOrdinaryBattle_EscaperFailReward() {
      return this.OrdinaryBattle_EscaperFailReward;
   }

   public double getFinalBattle_HunterWinReward() {
      return this.FinalBattle_HunterWinReward;
   }

   public double getOrdinaryBattle_HunterWinReward() {
      return this.OrdinaryBattle_HunterWinReward;
   }

   public double getFinalBattle_HunterFailReward() {
      return this.FinalBattle_HunterFailReward;
   }

   public double getOrdinaryBattle_HunterFailReward() {
      return this.OrdinaryBattle_HunterFailReward;
   }
}
