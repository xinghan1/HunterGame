package com.huntergame.Rank;

import com.huntergame.HunterGame;
import com.huntergame.data.DataStorageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class SeasonManager implements Listener {
    private final HunterGame plugin;
    private final RankManager rankManager;
    private final DataStorageManager dataStorageManager;

    // 文件存储作为备用
    private File seasonDataFile;
    private FileConfiguration seasonDataConfig;

    private String currentSeasonId; // 赛季ID为String类型
    private Map<String, List<String>> seasonRewards = new HashMap<>();

    public SeasonManager(HunterGame plugin, RankManager rankManager, DataStorageManager dataStorageManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.dataStorageManager = dataStorageManager;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        initSeasonDataFile();
        loadSeasonRewards();

        if (isUsingMySQL()) {
            initializeTables();
            // 从数据库加载当前赛季ID
            this.currentSeasonId = getCurrentSeasonIdFromDB();
            if (this.currentSeasonId == null || this.currentSeasonId.isEmpty()) {
                this.currentSeasonId = "S1";
                saveCurrentSeasonIdToDB(this.currentSeasonId);
            }
        } else {
            this.currentSeasonId = getCurrentSeasonIdFromFile();
            if (this.currentSeasonId == null || this.currentSeasonId.isEmpty()) {
                this.currentSeasonId = "S1";
                saveCurrentSeasonIdToFile(this.currentSeasonId);
            }
        }
        plugin.getLogger().info("已加载当前赛季，赛季ID: " + currentSeasonId);
    }

    /**
     * 开始一个新的赛季
     */
    public boolean startNewSeason(String newSeasonId) {
        if (newSeasonId == null || newSeasonId.trim().isEmpty()) {
            plugin.getLogger().warning("尝试使用空赛季ID开始新赛季！");
            return false;
        }
        newSeasonId = newSeasonId.trim();

        plugin.getLogger().info("正在准备新赛季: " + newSeasonId);

        // 1. 检查赛季ID是否已存在
        if (isSeasonIdExists(newSeasonId)) {
            plugin.getLogger().severe("无法开始新赛季！赛季ID '" + newSeasonId + "' 已存在。");
            return false;
        }

        // 重置所有玩家的赛季数据 (熟练度和段位)
        dataStorageManager.resetSeasonalData();

        // 更新赛季ID并重置领取状态
        if (isUsingMySQL()) {
            try (Connection conn = dataStorageManager.getConnection()) {
                conn.setAutoCommit(false);

                // a. 插入新赛季的系统记录
                String insertSql = "INSERT INTO season_info (season_id, player_uuid, season_start_time) VALUES (?, 'system', NOW())";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    pstmt.setString(1, newSeasonId);
                    pstmt.executeUpdate();
                }

                // b. 删除上一赛季的系统记录（如果存在）
                String deleteOldSystemSql = "DELETE FROM season_info WHERE player_uuid = 'system' AND season_id != ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteOldSystemSql)) {
                    pstmt.setString(1, newSeasonId);
                    pstmt.executeUpdate();
                }

                conn.commit(); // 提交事务
                this.currentSeasonId = newSeasonId;
            } catch (SQLException e) {
                plugin.getLogger().severe("新赛季开始时，MySQL 数据库操作失败！");
                e.printStackTrace();
                return false;
            }
        } else {
            // 文件存储逻辑
            this.currentSeasonId = newSeasonId;
            resetAllClaimedRewardsInFile();
            saveCurrentSeasonIdToFile(currentSeasonId);
        }

        plugin.getLogger().info("新赛季 '" + newSeasonId + "' 已成功开始！");
        return true;
    }

    /**
     * 检查一个赛季ID是否已经存在于数据库中
     */
    private boolean isSeasonIdExists(String seasonId) {
        if (!isUsingMySQL()) {
            return false;
        }
        String sql = "SELECT 1 FROM season_info WHERE season_id = ? LIMIT 1";
        try (Connection conn = dataStorageManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, seasonId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("检查赛季ID '" + seasonId + "' 是否存在时出错！");
            e.printStackTrace();
            return true; // 出错时为安全起见，阻止创建
        }
    }

    /**
     * 玩家加入时，检查并发放新赛季奖励
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 如果玩家是第一次在本赛季加入
        if (!hasPlayerClaimedSeasonReward(playerId)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // 获取玩家上赛季的最终熟练度
                double proficiency = dataStorageManager.getProficiency(player);
                String rank = rankManager.getRank(proficiency);

                // 执行对应段位的奖励命令
                if (seasonRewards.containsKey(rank)) {
                    for (String command : seasonRewards.get(rank)) {
                        String formattedCommand = command
                                .replace("%player%", player.getName())
                                .replace("%rank%", rank)
                                .replace("%season%", currentSeasonId);

                        Bukkit.getScheduler().runTask(plugin, () ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand)
                        );
                    }
                    player.sendMessage("§a=====================================");
                    player.sendMessage("§6         新赛季 '" + currentSeasonId + "' 开始啦！          ");
                    player.sendMessage("§a-------------------------------------");
                    player.sendMessage("§a你的上赛季段位为: §6" + rank);
                    player.sendMessage("§a赛季奖励已自动发放至你的账户！");
                    player.sendMessage("§a赛季努力冲分，可解锁更高级奖励！");
                    player.sendMessage("§a=====================================");
                } else {
                    plugin.getLogger().warning("玩家 " + player.getName() + " 的段位 " + rank + " 没有配置对应的赛季奖励。");
                }

                // 标记玩家已领取本赛季奖励
                markPlayerClaimedSeasonReward(playerId);
            });
        }
    }

    // --- 数据库操作方法 ---
    private boolean isUsingMySQL() {
        return "mysql".equalsIgnoreCase(plugin.getConfig().getString("database.type", "file"));
    }

    private void initializeTables() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS season_info (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "season_id VARCHAR(50) NOT NULL COMMENT '赛季的唯一ID (例如: S1, 2024_春季)', " +
                "player_uuid VARCHAR(36) NOT NULL COMMENT '已领取奖励的玩家UUID', " +
                "season_start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '赛季开始时间', " +
                "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '领取奖励的时间', " +
                "UNIQUE KEY unique_season_player (season_id, player_uuid) " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛季信息和玩家奖励领取记录表';";

        try (Connection conn = dataStorageManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            plugin.getLogger().severe("创建或初始化 season_info 表失败！");
            e.printStackTrace();
        }
    }

    private String getCurrentSeasonIdFromDB() {
        String sql = "SELECT season_id FROM season_info WHERE player_uuid = 'system' LIMIT 1";
        try (Connection conn = dataStorageManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("season_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("从数据库读取当前赛季ID失败！");
            e.printStackTrace();
        }
        return null;
    }

    private void saveCurrentSeasonIdToDB(String seasonId) {
        String sql = "INSERT INTO season_info (season_id, player_uuid) VALUES (?, 'system') ON DUPLICATE KEY UPDATE season_id = VALUES(season_id)";
        try (Connection conn = dataStorageManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, seasonId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("向数据库保存当前赛季ID失败！");
            e.printStackTrace();
        }
    }

    private boolean hasPlayerClaimedSeasonRewardInDB(UUID playerId) {
        String sql = "SELECT 1 FROM season_info WHERE season_id = ? AND player_uuid = ? LIMIT 1";
        try (Connection conn = dataStorageManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, currentSeasonId);
            pstmt.setString(2, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("检查玩家 " + playerId + " 是否已领取奖励时数据库出错！");
            e.printStackTrace();
            return false;
        }
    }

    private void markPlayerClaimedSeasonRewardInDB(UUID playerId) {
        String sql = "INSERT IGNORE INTO season_info (season_id, player_uuid) VALUES (?, ?)";
        try (Connection conn = dataStorageManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, currentSeasonId);
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("标记玩家 " + playerId + " 已领取奖励时数据库出错！");
            e.printStackTrace();
        }
    }

    // --- 文件操作方法 (已修正类型错误) ---
    private void initSeasonDataFile() {
        seasonDataFile = new File(plugin.getDataFolder(), "season_data.yml");
        if (!seasonDataFile.exists()) {
            try {
                seasonDataFile.getParentFile().mkdirs();
                seasonDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("创建赛季数据文件失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        seasonDataConfig = YamlConfiguration.loadConfiguration(seasonDataFile);
    }

    // 修正：返回类型应为 String
    private String getCurrentSeasonIdFromFile() {
        return seasonDataConfig.getString("current-season", null);
    }

    // 修正：参数类型应为 String
    private void saveCurrentSeasonIdToFile(String seasonId) {
        seasonDataConfig.set("current-season", seasonId);
        saveSeasonDataFile();
    }

    // 修正：实现了缺失的方法
    private boolean hasPlayerClaimedSeasonRewardFromFile(UUID playerId) {
        return seasonDataConfig.getBoolean("claimed-rewards." + playerId.toString(), false);
    }

    private void markPlayerClaimedSeasonRewardInFile(UUID playerId) {
        seasonDataConfig.set("claimed-rewards." + playerId.toString(), true);
        saveSeasonDataFile();
    }

    private void resetAllClaimedRewardsInFile() {
        if (seasonDataConfig.contains("claimed-rewards")) {
            seasonDataConfig.set("claimed-rewards", null);
            saveSeasonDataFile();
        }
    }

    private void saveSeasonDataFile() {
        try {
            seasonDataConfig.save(seasonDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存赛季数据文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- 公共方法 ---

    // 修正：移除了重复的方法定义
    public boolean hasPlayerClaimedSeasonReward(UUID playerId) {
        if (isUsingMySQL()) {
            return hasPlayerClaimedSeasonRewardInDB(playerId);
        } else {
            return hasPlayerClaimedSeasonRewardFromFile(playerId);
        }
    }

    public void markPlayerClaimedSeasonReward(UUID playerId) {
        if (isUsingMySQL()) {
            markPlayerClaimedSeasonRewardInDB(playerId);
        } else {
            markPlayerClaimedSeasonRewardInFile(playerId);
        }
    }

    // 获取赛季id
    public String getCurrentSeasonId() {
        return currentSeasonId;
    }


    public void loadSeasonRewards() {
        seasonRewards.clear();
        FileConfiguration rankConfig = loadRankConfig();
        if (rankConfig == null) return;
        if (!rankConfig.contains("season-rewards")) {
            plugin.getLogger().severe("rank.yml 中未找到 season-rewards 配置节点！");
            return;
        }
        ConfigurationSection rewardsSection = rankConfig.getConfigurationSection("season-rewards");
        if (rewardsSection == null) {
            plugin.getLogger().severe("rank.yml 中 season-rewards 配置节点格式错误！");
            return;
        }
        Set<String> rankKeys = rewardsSection.getKeys(false);
        for (String rankKey : rankKeys) {
            List<String> commands = rewardsSection.getStringList(rankKey);
            String cleanRankKey = rankKey.trim();
            seasonRewards.put(cleanRankKey, commands);
        }
        plugin.getLogger().info("赛季奖励加载完成，共加载 " + seasonRewards.size() + " 个段位的奖励");
    }

    private FileConfiguration loadRankConfig() {
        File rankFile = new File(plugin.getDataFolder(), "rank.yml");
        if (!rankFile.exists()) {
            plugin.getLogger().severe("rank.yml 文件不存在！请检查插件数据文件夹");
            return null;
        }
        return YamlConfiguration.loadConfiguration(rankFile);
    }


}