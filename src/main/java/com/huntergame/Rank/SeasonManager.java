package com.huntergame.rank;

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
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SeasonManager implements Listener {
    private final HunterGame plugin;
    private final RankManager rankManager;
    private final DataStorageManager dataStorageManager;

    // 文件存储作为备用
    private File seasonDataFile;
    private FileConfiguration seasonDataConfig;

    private volatile String currentSeasonId; // 多服刷新线程与主线程都会读取
    private Map<String, List<String>> seasonRewards = new HashMap<>();
    private CompletableFuture<Void> databaseReady = CompletableFuture.completedFuture(null);

    public SeasonManager(HunterGame plugin, RankManager rankManager, DataStorageManager dataStorageManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.dataStorageManager = dataStorageManager;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        initSeasonDataFile();
        loadSeasonRewards();

        if (isUsingMySQL()) {
            this.currentSeasonId = "S1";
            databaseReady = initializeDatabaseAsync();
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
    public CompletableFuture<Boolean> startNewSeasonAsync(String newSeasonId) {
        if (newSeasonId == null || newSeasonId.trim().isEmpty()) {
            plugin.getLogger().warning("尝试使用空赛季ID开始新赛季！");
            return CompletableFuture.completedFuture(false);
        }
        String seasonId = newSeasonId.trim();

        plugin.getLogger().info("正在准备新赛季: " + seasonId);

        if (isUsingMySQL()) {
            return databaseReady.thenCompose(ignored -> isSeasonIdExistsAsync(seasonId)).thenCompose(exists -> {
                if (exists) {
                    plugin.getLogger().severe("无法开始新赛季！赛季ID '" + seasonId + "' 已存在。");
                    return CompletableFuture.completedFuture(false);
                }
                return dataStorageManager.resetSeasonalDataAsync()
                        .thenCompose(ignored -> dataStorageManager.transactionAsync(transaction -> {
                            transaction.execute(
                                    "INSERT INTO season_info (season_id, player_uuid, season_start_time) VALUES (?, 'system', NOW())",
                                    Collections.singletonList(seasonId)
                            );
                            transaction.execute(
                                    "DELETE FROM season_info WHERE player_uuid = 'system' AND season_id != ?",
                                    Collections.singletonList(seasonId)
                            );
                        }))
                        .thenApply(ignored -> {
                            currentSeasonId = seasonId;
                            plugin.getLogger().info("新赛季 '" + seasonId + "' 已成功开始！");
                            return true;
                        });
            }).exceptionally(error -> {
                plugin.getLogger().severe("新赛季数据库操作失败: " + messageOf(error));
                return false;
            });
        }

        dataStorageManager.resetSeasonalData();
        this.currentSeasonId = seasonId;
        resetAllClaimedRewardsInFile();
        saveCurrentSeasonIdToFile(currentSeasonId);
        plugin.getLogger().info("新赛季 '" + seasonId + "' 已成功开始！");
        return CompletableFuture.completedFuture(true);
    }

    /**
     * 检查一个赛季ID是否已经存在于数据库中
     */
    private CompletableFuture<Boolean> isSeasonIdExistsAsync(String seasonId) {
        String sql = "SELECT 1 FROM season_info WHERE season_id = ? LIMIT 1";
        return dataStorageManager.queryAsync(sql, Collections.singletonList(seasonId))
                .thenApply(rows -> !rows.isEmpty());
    }

    /**
     * 玩家加入时，检查并发放新赛季奖励
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        if (isUsingMySQL()) {
            databaseReady
                    .thenCompose(ignored -> dataStorageManager.refreshPlayerAsync(playerId))
                    .thenApply(ignored -> {
                        if (plugin.getHunterGamePlaceholder() != null) {
                            plugin.getHunterGamePlaceholder().refreshAllTiersSilently();
                        }
                        return ignored;
                    })
                    .thenCompose(ignored -> refreshCurrentSeasonIdAsync())
                    .thenCompose(ignored -> claimSeasonRewardAsync(playerId))
                    .thenAccept(claimed -> {
                if (claimed) grantSeasonReward(playerId, playerName, dataStorageManager.getProficiency(playerId));
            }).exceptionally(error -> {
                plugin.getLogger().warning("检查赛季奖励失败: " + messageOf(error));
                return null;
            });
        } else if (!hasPlayerClaimedSeasonRewardFromFile(playerId)) {
            grantSeasonReward(playerId, playerName, dataStorageManager.getProficiency(playerId));
        }
    }

    private void grantSeasonReward(UUID playerId, String playerName, double proficiency) {
        String rank = rankManager.getRank(proficiency);
        String seasonId = currentSeasonId;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                if (isUsingMySQL()) releaseSeasonRewardClaim(playerId, seasonId);
                return;
            }

            List<String> commands = seasonRewards.get(rank);
            if (commands != null) {
                for (String command : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                            .replace("%player%", playerName)
                            .replace("%rank%", rank)
                            .replace("%season%", seasonId));
                }
                for (String line : plugin.getMessageList("season_reward_message", Arrays.asList(
                        "&a=====================================",
                        "&6         新赛季 '%season%' 开始啦！          ",
                        "&a-------------------------------------",
                        "&a你的上赛季段位为: &6%rank%",
                        "&a赛季奖励已自动发放至你的账户！",
                        "&a赛季努力冲分，可解锁更高级奖励！",
                        "&a====================================="
                ))) player.sendMessage(line.replace("%season%", seasonId).replace("%rank%", rank));
            } else {
                plugin.getLogger().warning("玩家 " + playerName + " 的段位 " + rank + " 没有配置对应的赛季奖励。");
            }
            if (!isUsingMySQL()) markPlayerClaimedSeasonRewardInFile(playerId);
        });
    }

    // --- 数据库操作方法 ---
    private boolean isUsingMySQL() {
        return dataStorageManager.isUsingUnifiedDatabase();
    }

    private CompletableFuture<Void> initializeDatabaseAsync() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS season_info (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "season_id VARCHAR(50) NOT NULL COMMENT '赛季的唯一ID (例如: S1, 2024_春季)', " +
                "player_uuid VARCHAR(36) NOT NULL COMMENT '已领取奖励的玩家UUID', " +
                "season_start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '赛季开始时间', " +
                "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '领取奖励的时间', " +
                "UNIQUE KEY unique_season_player (season_id, player_uuid) " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛季信息和玩家奖励领取记录表';";

        return dataStorageManager.executeAsync(createTableSQL, Collections.emptyList())
                .thenCompose(ignored -> dataStorageManager.queryAsync(
                        "SELECT season_id FROM season_info WHERE player_uuid = 'system' LIMIT 1",
                        Collections.emptyList()))
                .thenCompose(rows -> {
                    if (!rows.isEmpty()) {
                        currentSeasonId = String.valueOf(rows.get(0).get("season_id"));
                        return CompletableFuture.completedFuture(0);
                    }
                    currentSeasonId = "S1";
                    return dataStorageManager.executeAsync(
                            "INSERT INTO season_info (season_id, player_uuid) VALUES (?, 'system')",
                            Collections.singletonList(currentSeasonId));
                }).thenRun(() -> plugin.getLogger().info("已异步加载当前赛季，赛季ID: " + currentSeasonId))
                .exceptionally(error -> {
                    plugin.getLogger().severe("初始化赛季数据库失败: " + messageOf(error));
                    return null;
                });
    }

    private CompletableFuture<Void> refreshCurrentSeasonIdAsync() {
        return dataStorageManager.queryAsync(
                        "SELECT season_id FROM season_info WHERE player_uuid = 'system' LIMIT 1",
                        Collections.emptyList())
                .thenAccept(rows -> {
                    if (!rows.isEmpty()) currentSeasonId = String.valueOf(rows.get(0).get("season_id"));
                })
                .whenComplete((ignored, error) -> {
                    if (error != null) plugin.getLogger().warning("跨服赛季信息刷新失败: " + messageOf(error));
                });
    }

    private CompletableFuture<Boolean> claimSeasonRewardAsync(UUID playerId) {
        String seasonId = currentSeasonId;
        String sql = "INSERT IGNORE INTO season_info (season_id, player_uuid) VALUES (?, ?)";
        return dataStorageManager.executeAsync(sql, Arrays.asList(seasonId, playerId.toString()))
                .thenApply(affectedRows -> affectedRows > 0);
    }

    private void releaseSeasonRewardClaim(UUID playerId, String seasonId) {
        dataStorageManager.executeAsync(
                "DELETE FROM season_info WHERE season_id = ? AND player_uuid = ?",
                Arrays.asList(seasonId, playerId.toString()));
    }

    private void markPlayerClaimedSeasonRewardInDB(UUID playerId) {
        String sql = "INSERT IGNORE INTO season_info (season_id, player_uuid) VALUES (?, ?)";
        dataStorageManager.executeAsync(sql, Arrays.asList(currentSeasonId, playerId.toString()))
                .exceptionally(error -> {
                    plugin.getLogger().severe("标记玩家 " + playerId + " 已领取奖励时数据库出错: " + messageOf(error));
                    return 0;
                });
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
            return false;
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

    private String messageOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
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
