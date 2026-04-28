package com.huntergame.data;

import com.huntergame.HunterGame;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DataStorageManager {

    private final HunterGame plugin;
    private final Map<UUID, Integer> killCache = new HashMap<>();

    private HikariDataSource dataSource;
    private String databaseType;
    private String tableName;

    private File dataFile;
    private FileConfiguration fileConfig;
    private boolean fileDirty = false;
    private int fileSaveTaskId = -1;

    public DataStorageManager(HunterGame plugin) {
        this.plugin = plugin;
        this.databaseType = plugin.getConfig().getString("database.type", "file").toLowerCase();
        String prefix = plugin.getConfig().getString("database.table_prefix", "");
        this.tableName = prefix + "player_stats";
        setupStorage();
    }

    private void setupStorage() {
        if ("mysql".equalsIgnoreCase(databaseType)) {
            setupMySQL();
        } else {
            plugin.getLogger().warning("未指定有效的数据库类型或类型为 'file'，将使用本地文件存储！");
            setupFileStorage();
        }
    }

    private void setupMySQL() {
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.name", "minecraft");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");
        String timeZone = plugin.getConfig().getString("database.timezone", "UTC");
        boolean useSSL = plugin.getConfig().getBoolean("database.use_ssl", false);

        try {
            // 增加参数以确保兼容性
            String jdbcUrl = String.format(
                    "jdbc:mysql://%s:%d/%s" +
                            "?serverTimezone=%s" +
                            "&useSSL=%b" +
                            "&allowPublicKeyRetrieval=true" +
                            "&characterEncoding=utf-8" +
                            "&rewriteBatchedStatements=true",
                    host, port, database, timeZone, useSSL
            );

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool_size", 5));
            config.setMinimumIdle(2);
            config.setConnectionTimeout(5000);

            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("MySQL 连接池初始化成功！");

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // 修复 1: 给 player_rank 加上反引号（虽然 player_rank 本身不是关键字，但 rank 是）
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                                "uuid VARCHAR(36) PRIMARY KEY," +
                                "name VARCHAR(36) DEFAULT ''," +
                                "kills INT DEFAULT 0," +
                                "kills_put INT DEFAULT 0," +
                                "deaths INT DEFAULT 0," +
                                "games_played INT DEFAULT 0," +
                                "hunter_wins INT DEFAULT 0," +
                                "escape_wins INT DEFAULT 0," +
                                "total_wins INT DEFAULT 0," +
                                "proficiency DOUBLE DEFAULT 0.0," +
                                "`rank` VARCHAR(36) DEFAULT ''" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL 初始化失败！" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupFileStorage() {
        dataFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file_path", "player_data.yml"));
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        fileConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void resetSeasonalData() {
        if ("mysql".equalsIgnoreCase(databaseType) && dataSource != null) {
            // 修复 2: 关键字增加反引号
            String sql = "UPDATE " + tableName + " SET proficiency = 0.0, `rank` = ''";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else if ("file".equalsIgnoreCase(databaseType) && fileConfig != null) {
            Set<String> keys = fileConfig.getKeys(false);
            for (String uuidStr : keys) {
                if (isValidUUID(uuidStr)) {
                    fileConfig.set(uuidStr + ".proficiency", 0.0);
                    fileConfig.set(uuidStr + ".rank", ""); // 修复 3: 修正变量名错误
                }
            }
            saveFileConfig();
        }
    }

    // --- 数据操作方法 ---

    public void addKill(UUID playerId) {
        killCache.put(playerId, killCache.getOrDefault(playerId, 0) + 1);
    }

    public int getKills(UUID playerId) {
        return killCache.getOrDefault(playerId, 0);
    }

    public void resetKillCache() {
        killCache.clear();
    }

    public void addKillput(UUID playerId, Player player) {
        String key = playerId.toString() + ".kills_put";
        int newValue = getValue(playerId, "kills_put") + 1;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "kills_put", newValue);
        } else {
            fileConfig.set(key, newValue);
            saveFileConfig();
        }
    }

    public int getKillsput(UUID playerId) {
        return getValue(playerId, "kills_put");
    }

    public void addDeath(UUID playerId, Player player) {
        String key = playerId.toString() + ".deaths";
        int newValue = getValue(playerId, "deaths") + 1;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "deaths", newValue);
        } else {
            fileConfig.set(key, newValue);
            saveFileConfig();
        }
    }

    public int getDeaths(UUID playerId) {
        return getValue(playerId, "deaths");
    }

    public void addGamePlayed(UUID playerId, Player player) {
        String key = playerId.toString() + ".games_played";
        int newValue = getValue(playerId, "games_played") + 1;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "games_played", newValue);
        } else {
            fileConfig.set(key, newValue);
            saveFileConfig();
        }
    }

    public int getGamesPlayed(UUID playerId) {
        return getValue(playerId, "games_played");
    }

    public void addHunterWin(UUID playerId, Player player) {
        String key = playerId.toString() + ".hunter_wins";
        int newValue = getValue(playerId, "hunter_wins") + 1;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "hunter_wins", newValue);
        } else {
            fileConfig.set(key, newValue);
            saveFileConfig();
        }
        saveTotalWins(playerId, player);
    }

    public int getHunterWin(UUID playerId) {
        return getValue(playerId, "hunter_wins");
    }

    public void addEscapeWin(UUID playerId, Player player) {
        String key = playerId.toString() + ".escape_wins";
        int newValue = getValue(playerId, "escape_wins") + 1;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "escape_wins", newValue);
        } else {
            fileConfig.set(key, newValue);
            saveFileConfig();
        }
        saveTotalWins(playerId, player);
    }

    public int getEscapeWin(UUID playerId) {
        return getValue(playerId, "escape_wins");
    }

    public void saveTotalWins(UUID playerId, Player player) {
        int totalWins = getEscapeWin(playerId) + getHunterWin(playerId);
        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "total_wins", totalWins);
        } else {
            String key = playerId.toString() + ".total_wins";
            fileConfig.set(key, totalWins);
            saveFileConfig();
        }
    }

    public int getTotalWins(UUID playerId) {
        return getEscapeWin(playerId) + getHunterWin(playerId);
    }

    public void addProficiency(Player player, double count) {
        UUID playerId = player.getUniqueId();
        double newValue = getProficiency(player) + count;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "proficiency", newValue);
        } else {
            String key = playerId.toString() + ".proficiency";
            fileConfig.set(key, newValue);
            saveFileConfig();
        }
    }

    public double getProficiency(Player player) {
        UUID playerId = player.getUniqueId();
        double proficiency = 0.0;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            String sql = "SELECT proficiency FROM " + tableName + " WHERE uuid = ?";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    proficiency = rs.getDouble("proficiency");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            String key = playerId.toString() + ".proficiency";
            proficiency = fileConfig.getDouble(key, 0.0);
        }
        return new BigDecimal(proficiency).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    
    public void addRank(UUID playerId, Player player) {
        double proficiency = getProficiency(player);
        String rankName = plugin.getRankManager().getRankName(proficiency);
        if ("mysql".equalsIgnoreCase(databaseType)) {
            updateDatabase(playerId, player, "rank", rankName);
        } else {
            fileConfig.set(playerId.toString() + ".rank", rankName);
            saveFileConfig();
        }
    }

    /**
     * 核心修复：通用的更新数据库方法
     * 增加了对 column 的反引号包裹，防止 rank 等关键字导致语法错误
     */
    private void updateDatabase(UUID uuid, Player player, String column, Object value) {
        if (dataSource == null) return;

        // 修复 4: 关键点！给 `" + column + "` 增加了反引号包裹
        String sql = "INSERT INTO " + tableName + " (uuid, name, `" + column + "`) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), `" + column + "` = VALUES(`" + column + "`)";

        try (Connection conn = getConnection(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, player.getName());

            if (value instanceof Integer) statement.setInt(3, (Integer) value);
            else if (value instanceof Double) statement.setDouble(3, (Double) value);
            else if (value instanceof String) statement.setString(3, (String) value);

            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("更新数据库失败 for player " + uuid + " (" + player.getName() + ")");
            e.printStackTrace();
        }
    }

    private int getValue(UUID playerId, String field) {
        if ("mysql".equalsIgnoreCase(databaseType)) {
            // 修复 5: 查询语句也增加反引号
            String sql = "SELECT `" + field + "` FROM " + tableName + " WHERE uuid = ?";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return rs.getInt(field);
            } catch (SQLException ignored) {}
        } else {
            return fileConfig.getInt(playerId.toString() + "." + field, 0);
        }
        return 0;
    }

    private void saveFileConfig() {
        if (fileConfig == null || dataFile == null) {
            return;
        }

        fileDirty = true;
        if (fileSaveTaskId != -1) {
            return;
        }

        fileSaveTaskId = plugin.getServer().getScheduler().runTaskLater(plugin, this::flushFileConfig, 20L).getTaskId();
    }

    private void flushFileConfig() {
        if (!fileDirty || fileConfig == null || dataFile == null) {
            fileSaveTaskId = -1;
            return;
        }

        try {
            fileConfig.save(dataFile);
            fileDirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("保存玩家数据失败: " + e.getMessage());
        } finally {
            fileSaveTaskId = -1;
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("数据源未初始化。");
        return dataSource.getConnection();
    }

    private boolean isValidUUID(String uuidStr) {
        try { UUID.fromString(uuidStr); return true; } catch (Exception e) { return false; }
    }

    public Map<UUID, Integer> getAllPlayerTiers() {
        Map<UUID, Integer> tiers = new HashMap<>();
        if ("mysql".equalsIgnoreCase(databaseType) && dataSource != null) {
            String sql = "SELECT uuid, RANK() OVER (ORDER BY proficiency DESC) AS tier FROM " + tableName + " WHERE proficiency > 0";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) tiers.put(UUID.fromString(rs.getString("uuid")), rs.getInt("tier"));
            } catch (SQLException e) { e.printStackTrace(); }
        } else if (fileConfig != null) {
            Map<UUID, Double> profMap = new HashMap<>();
            for (String key : fileConfig.getKeys(false)) {
                if (isValidUUID(key)) {
                    double prof = fileConfig.getDouble(key + ".proficiency", 0.0);
                    if (prof > 0) profMap.put(UUID.fromString(key), prof);
                }
            }
            java.util.List<Map.Entry<UUID, Double>> sorted = new java.util.ArrayList<>(profMap.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : sorted) tiers.put(entry.getKey(), rank++);
        }
        return tiers;
    }

    public void shutdown() {
        if (fileSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(fileSaveTaskId);
            fileSaveTaskId = -1;
        }
        flushFileConfig();
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
