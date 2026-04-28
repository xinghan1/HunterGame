package com.huntergame.Rank;

import com.huntergame.HunterGame;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class RankManager {
    private final HunterGame plugin;
    private File rankFile;
    private FileConfiguration rankConfig;

    // 段位列表
    private final List<Rank> ranks = new ArrayList<>();

    // 奖励配置
    private double GameStartReward; // 游戏开始奖励

    private double FinalBattle_EscaperKillReward; // 终章之逃 生者击杀
    private double SkillBattle_EscaperKillReward; // 技能之战 逃生者击杀
    private double OrdinaryBattle_EscaperKillReward; // 原版猎人 逃生者击杀

    private double FinalBattle_HunterKillReward; // 终章之战 猎人击杀
    private double SkillBattle_HunterKillReward; // 技能之战 猎人击杀
    private double OrdinaryBattle_HunterKillReward; // 原版猎人 猎人击杀

    private double FinalBattle_EscaperDeathReward; // 终章之战 逃生者死亡
    private double SkillBattle_EscaperDeathReward; // 技能之战 逃生者死亡
    private double OrdinaryBattle_EscaperDeathReward; // 原版猎人 逃生者死亡

    private double FinalBattle_HunterDeathReward; // 终章之战 猎人死亡
    private double SkillBattle_HunterDeathReward; // 技能之战 猎人死亡
    private double OrdinaryBattle_HunterDeathReward; // 原版猎人 猎人死亡

    private double FinalBattle_EscaperWinReward; // 终章之战 逃生者胜利
    private double SkillBattle_EscaperWinReward; // 技能之战 逃生者胜利
    private double OrdinaryBattle_EscaperWinReward; // 原版猎人 逃生者胜利

    private double FinalBattle_HunterFailReward; // 终章之战 猎人失败
    private double SkillBattle_HunterFailReward; // 技能之战 猎人失败
    private double OrdinaryBattle_HunterFailReward; // 原版猎人 猎人失败

    private double FinalBattle_EscaperFailReward; // 终章之战 逃生者失败
    private double SkillBattle_EscaperFailReward; // 技能之战 逃生者失败
    private double OrdinaryBattle_EscaperFailReward; // 原版猎人 逃生者失败

    private double FinalBattle_HunterWinReward; // 终章之战 猎人胜利
    private double SkillBattle_HunterWinReward; // 技能之战 猎人胜利
    private double OrdinaryBattle_HunterWinReward; // 原版猎人 猎人胜利


    // 存储段位对应的赛季奖励命令
    private final Map<String, List<String>> seasonRewards = new HashMap<>();
    private final Map<String, Double> mobTypeRewards = new HashMap<>();

    public RankManager(HunterGame plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        reloadConfig();
    }

    // 保存默认配置
    public void saveDefaultConfig() {
        rankFile = new File(plugin.getDataFolder(), "rank.yml");
        if (!rankFile.exists()) {
            plugin.saveResource("rank.yml", false);
        }
    }

    // 重新加载配置
    public void reloadConfig() {
        ranks.clear();
        mobTypeRewards.clear();
        seasonRewards.clear();
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);

        // 加载段位
        ConfigurationSection ranksSection = rankConfig.getConfigurationSection("ranks");
        if (ranksSection != null) {
            List<Integer> keys = new ArrayList<>();
            for (String key : ranksSection.getKeys(false)) {
                try {
                    keys.add(Integer.parseInt(key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("段位 key 必须是整数: " + key);
                }
            }
            Collections.sort(keys);

            for (int key : keys) {
                ConfigurationSection section = ranksSection.getConfigurationSection(String.valueOf(key));
                if (section == null) continue;

                String name = section.getString("name", "未命名段位");
                double min = section.getDouble("min", 0.0);
                double max = section.getDouble("max", 0.0);

                ranks.add(new Rank(name, min, max));
            }
        }

        // 加载奖励
        ConfigurationSection rewardsSection = rankConfig.getConfigurationSection("proficiency-rewards");
        if (rewardsSection != null) {
            GameStartReward = rewardsSection.getDouble("GameStartReward", 0.3);

            FinalBattle_EscaperKillReward = rewardsSection.getDouble("FinalBattle_EscaperKillReward", 0.5);
            SkillBattle_EscaperKillReward = rewardsSection.getDouble("SkillBattle_EscaperKillReward", 0.3);
            OrdinaryBattle_EscaperKillReward = rewardsSection.getDouble("OrdinaryBattle_EscaperKillReward", 0.4);

            FinalBattle_HunterKillReward = rewardsSection.getDouble("FinalBattle_HunterKillReward", 0.5);
            SkillBattle_HunterKillReward = rewardsSection.getDouble("SkillBattle_HunterKillReward", 0.3);
            OrdinaryBattle_HunterKillReward = rewardsSection.getDouble("OrdinaryBattle_HunterKillReward", 0.4);

            FinalBattle_EscaperDeathReward = rewardsSection.getDouble("FinalBattle_EscaperDeathReward", -1.5);
            SkillBattle_EscaperDeathReward = rewardsSection.getDouble("SkillBattle_EscaperDeathReward", -1.5);
            OrdinaryBattle_EscaperDeathReward = rewardsSection.getDouble("OrdinaryBattle_EscaperDeathReward", -1);

            FinalBattle_HunterDeathReward = rewardsSection.getDouble("FinalBattle_HunterDeathReward", -0.3);
            SkillBattle_HunterDeathReward = rewardsSection.getDouble("SkillBattle_HunterDeathReward", -0.4);
            OrdinaryBattle_HunterDeathReward = rewardsSection.getDouble("OrdinaryBattle_HunterDeathReward", -0.4);

            FinalBattle_EscaperWinReward = rewardsSection.getDouble("FinalBattle_EscaperWinReward", 3);
            SkillBattle_EscaperWinReward = rewardsSection.getDouble("SkillBattle_EscaperWinReward", 7);
            OrdinaryBattle_EscaperWinReward = rewardsSection.getDouble("OrdinaryBattle_EscaperWinReward", 8);

            FinalBattle_EscaperFailReward = rewardsSection.getDouble("FinalBattle_EscaperFailReward", -1);
            SkillBattle_EscaperFailReward = rewardsSection.getDouble("SkillBattle_EscaperFailReward", -2);
            OrdinaryBattle_EscaperFailReward = rewardsSection.getDouble("OrdinaryBattle_EscaperFailReward", -2);

            FinalBattle_HunterWinReward = rewardsSection.getDouble("FinalBattle_HunterWinReward", 1);
            SkillBattle_HunterWinReward = rewardsSection.getDouble("SkillBattle_HunterWinReward", 2);
            OrdinaryBattle_HunterWinReward = rewardsSection.getDouble("OrdinaryBattle_HunterWinReward", 1.5);

            FinalBattle_HunterFailReward = rewardsSection.getDouble("FinalBattle_HunterFailReward", -1);
            SkillBattle_HunterFailReward = rewardsSection.getDouble("SkillBattle_HunterFailReward", -1.5);
            OrdinaryBattle_HunterFailReward = rewardsSection.getDouble("OrdinaryBattle_HunterFailReward", -1.5);
        }


        // 加载赛季奖励
        ConfigurationSection seasonSection = rankConfig.getConfigurationSection("season-rewards");
        if (seasonSection != null) {
            for (String rankName : seasonSection.getKeys(false)) {
                List<String> commands = seasonSection.getStringList(rankName);
                seasonRewards.put(rankName, commands);
            }
        }

    }

    public Map<String, List<String>> getSeasonRewards() {
        return seasonRewards;
    }

    // 根据积分获取段位
    public String getRankName(double score) {
        for (Rank rank : ranks) {
            if (score >= rank.getMin() && score < rank.getMax()) {
                return rank.getName();
            }
        }
        return ranks.isEmpty() ? "无段位" : ranks.get(ranks.size() - 1).getName();
    }

    // 获取下一段位所需积分
    public double getNextRankRequired(double score) {
        for (Rank rank : ranks) {
            if (score < rank.getMin()) {
                return rank.getMin();
            }
        }
        return -1; // 已是最高段位
    }

    public String getRank(double score) {
        for (Rank rank : ranks) {
            if (score >= rank.getMin() && score < rank.getMax()) {
                return rank.getName();
            }
        }
        return ranks.isEmpty() ? "无段位" : ranks.get(ranks.size() - 1).getName();
    }

    public double getGameStartReward() {
        return GameStartReward;
    }

    public double getFinalBattle_EscaperKillReward() {
        return FinalBattle_EscaperKillReward;
    }
    public double getSkillBattle_EscaperKillReward() {
        return SkillBattle_EscaperKillReward;
    }
    public double getOrdinaryBattle_EscaperKillReward() {
        return OrdinaryBattle_EscaperKillReward;
    }

    public double getFinalBattle_HunterKillReward() {
        return FinalBattle_HunterKillReward;
    }
    public double getSkillBattle_HunterKillReward() {
        return SkillBattle_HunterKillReward;
    }
    public double getOrdinaryBattle_HunterKillReward() {
        return OrdinaryBattle_HunterKillReward;
    }

    public double getFinalBattle_EscaperDeathReward() {
        return FinalBattle_EscaperDeathReward;
    }
    public double getSkillBattle_EscaperDeathReward() {
        return SkillBattle_EscaperDeathReward;
    }
    public double getOrdinaryBattle_EscaperDeathReward() {
        return OrdinaryBattle_EscaperDeathReward;
    }

    public double getFinalBattle_HunterDeathReward() {
        return FinalBattle_HunterDeathReward;
    }
    public double getSkillBattle_HunterDeathReward() {
        return SkillBattle_HunterDeathReward;
    }
    public double getOrdinaryBattle_HunterDeathReward() {
        return OrdinaryBattle_HunterDeathReward;
    }

    public double getFinalBattle_EscaperWinReward() {
        return FinalBattle_EscaperWinReward;
    }
    public double getSkillBattle_EscaperWinReward() {
        return SkillBattle_EscaperWinReward;
    }
    public double getOrdinaryBattle_EscaperWinReward() {
        return OrdinaryBattle_EscaperWinReward;
    }

    public double getFinalBattle_EscaperFailReward() {
        return FinalBattle_EscaperFailReward;
    }
    public double getSkillBattle_EscaperFailReward() {
        return SkillBattle_EscaperFailReward;
    }
    public double getOrdinaryBattle_EscaperFailReward() {
        return OrdinaryBattle_EscaperFailReward;
    }

    public double getFinalBattle_HunterWinReward() {
        return FinalBattle_HunterWinReward;
    }
    public double getSkillBattle_HunterWinReward() {
        return SkillBattle_HunterWinReward;
    }
    public double getOrdinaryBattle_HunterWinReward() {
        return OrdinaryBattle_HunterWinReward;
    }

    public double getFinalBattle_HunterFailReward() {
        return FinalBattle_HunterFailReward;
    }
    public double getSkillBattle_HunterFailReward() {
        return SkillBattle_HunterFailReward;
    }
    public double getOrdinaryBattle_HunterFailReward() {
        return OrdinaryBattle_HunterFailReward;
    }

}
