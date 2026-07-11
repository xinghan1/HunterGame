package com.huntergame.data;

import com.huntergame.HunterGame;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DataStorageManager {

    private static final String[] PERSISTED_FIELDS = {
            "kills_put", "deaths", "games_played", "hunter_wins",
            "escape_wins", "total_wins", "proficiency", "rank"
    };

    private final HunterGame plugin;
    private final Map<UUID, Integer> killCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private final ExecutorService fileExecutor;
    private final File dataFile;
    private final String tableName;

    private UnifiedDatabaseClient databaseClient;
    private boolean unifiedDatabase;
    private volatile boolean shuttingDown;
    private CompletableFuture<Void> readyFuture;
    private CompletableFuture<Void> writeTail;

    public DataStorageManager(HunterGame plugin) {
        this.plugin = plugin;
        this.tableName = sanitizeIdentifier(plugin.getConfig().getString("database.table_prefix", "") + "player_stats");
        this.dataFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file_path", "player_data.yml"));
        this.fileExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "HunterGame-FileStorage");
            thread.setDaemon(true);
            return thread;
        });

        this.unifiedDatabase = setupMySqlClient();
        if (unifiedDatabase) {
            readyFuture = initializeMySql();
        } else {
            loadFileStorage();
            readyFuture = CompletableFuture.completedFuture(null);
        }
        writeTail = readyFuture.handle((ignored, error) -> null);
    }

    private boolean setupMySqlClient() {
        if (!"mysql".equalsIgnoreCase(plugin.getConfig().getString("database.type", "file"))) {
            plugin.getLogger().info("玩家数据使用本地文件存储。");
            return false;
        }

        Plugin databasePlugin = plugin.getServer().getPluginManager().getPlugin("database");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            try {
                databaseClient = DatabaseApiClient.create();
                if (databaseClient != null) {
                    plugin.getLogger().info("已接入 database 统一 MySQL 服务，所有 SQL 将异步执行。");
                    return true;
                }
                plugin.getLogger().warning("database 插件的 MySQL 未启用，将使用内置 HikariCP。");
            } catch (LinkageError | RuntimeException error) {
                plugin.getLogger().warning("database 软依赖不可用，将使用内置 HikariCP: " + error.getMessage());
            }
        } else {
            plugin.getLogger().info("未找到 database 软依赖，将使用内置 HikariCP。");
        }

        try {
            databaseClient = new BuiltInMySqlClient(plugin.getConfig());
            plugin.getLogger().info("内置 HikariCP 初始化成功，所有 SQL 将在专用数据库线程执行。");
            return true;
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().severe("内置 MySQL 初始化失败，已回退到本地文件存储: " + error.getMessage());
            return false;
        }
    }

    private CompletableFuture<Void> initializeMySql() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "uuid VARCHAR(36) PRIMARY KEY COMMENT '玩家 UUID'," +
                "name VARCHAR(36) DEFAULT '' COMMENT '玩家名称'," +
                "kills_put INT DEFAULT 0 COMMENT '总击杀数'," +
                "deaths INT DEFAULT 0 COMMENT '死亡次数'," +
                "games_played INT DEFAULT 0 COMMENT '游玩次数'," +
                "hunter_wins INT DEFAULT 0 COMMENT '猎人胜场'," +
                "escape_wins INT DEFAULT 0 COMMENT '逃生者胜场'," +
                "total_wins INT DEFAULT 0 COMMENT '总胜场'," +
                "proficiency DOUBLE DEFAULT 0.0 COMMENT '熟练度'," +
                "`rank` VARCHAR(36) DEFAULT '' COMMENT '段位'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HunterGame 玩家统计';";

        return databaseClient.execute(createTableSql, Collections.emptyList())
                .thenCompose(ignored -> databaseClient.query("SELECT * FROM " + tableName, Collections.emptyList()))
                .thenAccept(rows -> {
                    for (Map<String, Object> row : rows) {
                        UUID uuid = UUID.fromString(String.valueOf(row.get("uuid")));
                        PlayerStats persisted = PlayerStats.fromRow(row);
                        statsCache.merge(uuid, persisted, PlayerStats::mergePersistedBase);
                    }
                    plugin.getLogger().info("已异步加载 " + rows.size() + " 条玩家统计数据。");
                })
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().severe("初始化统一数据库失败: " + messageOf(error));
                    }
                });
    }

    private void loadFileStorage() {
        if (!dataFile.exists()) {
            try {
                File parent = dataFile.getParentFile();
                if (parent != null) parent.mkdirs();
                dataFile.createNewFile();
            } catch (IOException error) {
                plugin.getLogger().severe("创建玩家数据文件失败: " + error.getMessage());
                return;
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                statsCache.put(uuid, PlayerStats.fromConfig(config, key));
            } catch (IllegalArgumentException ignored) {
                // Ignore non-player top-level keys left by older configurations.
            }
        }
    }

    public boolean isUsingUnifiedDatabase() {
        return unifiedDatabase;
    }

    public CompletableFuture<Void> ready() {
        return readyFuture;
    }

    public CompletableFuture<Void> refreshPlayerAsync(UUID uuid) {
        if (!unifiedDatabase || shuttingDown) return CompletableFuture.completedFuture(null);
        Map<UUID, Long> version = new HashMap<>();
        PlayerStats stats = statsCache.get(uuid);
        if (stats != null) {
            synchronized (stats) { version.put(uuid, stats.mutationVersion); }
        }
        return readyFuture.thenCompose(ignored -> databaseClient.query(
                        "SELECT * FROM " + tableName + " WHERE uuid = ?",
                        Collections.singletonList(uuid.toString())))
                .thenAccept(rows -> applyRefreshedRows(rows, version))
                .exceptionally(error -> {
                    if (!shuttingDown) plugin.getLogger().warning("玩家跨服数据刷新失败: " + messageOf(error));
                    return null;
                });
    }

    private void applyRefreshedRows(List<Map<String, Object>> rows, Map<UUID, Long> versionsAtStart) {
        for (Map<String, Object> row : rows) {
            UUID uuid = UUID.fromString(String.valueOf(row.get("uuid")));
            PlayerStats persisted = PlayerStats.fromRow(row);
            statsCache.compute(uuid, (ignored, current) -> {
                if (current == null) return persisted;
                Long expectedVersion = versionsAtStart.get(uuid);
                synchronized (current) {
                                if (expectedVersion != null
                                        && current.mutationVersion == expectedVersion
                                        && current.pendingWrites == 0) {
                        current.replaceWith(persisted);
                    }
                }
                return current;
            });
        }
    }

    public CompletableFuture<List<Map<String, Object>>> queryAsync(String sql, List<Object> params) {
        if (!unifiedDatabase) return failedFuture(new IllegalStateException("统一 MySQL 未启用"));
        return readyFuture.thenCompose(ignored -> databaseClient.query(sql, safeParams(params)));
    }

    public CompletableFuture<Integer> executeAsync(String sql, List<Object> params) {
        if (!unifiedDatabase) return failedFuture(new IllegalStateException("统一 MySQL 未启用"));
        return readyFuture.thenCompose(ignored -> databaseClient.execute(sql, safeParams(params)));
    }

    public CompletableFuture<Void> transactionAsync(UnifiedDatabaseClient.TransactionAction action) {
        if (!unifiedDatabase) return failedFuture(new IllegalStateException("统一 MySQL 未启用"));
        return readyFuture.thenCompose(ignored -> databaseClient.transaction(action));
    }

    public CompletableFuture<Void> resetSeasonalDataAsync() {
        List<PlayerStats> affected = new ArrayList<>(statsCache.values());
        for (PlayerStats stats : affected) {
            synchronized (stats) {
                stats.proficiency = 0.0;
                stats.rank = "";
                stats.mutationVersion++;
                if (unifiedDatabase) stats.pendingWrites++;
            }
        }
        CompletableFuture<Void> operation = enqueueWrite(() -> {
            if (unifiedDatabase) {
                return databaseClient.execute(
                        "UPDATE " + tableName + " SET proficiency = 0.0, `rank` = ''",
                        Collections.emptyList()
                );
            }
            return saveFileSnapshotAsync();
        });
        if (unifiedDatabase) operation.whenComplete((ignored, error) -> {
            for (PlayerStats stats : affected) {
                synchronized (stats) { stats.pendingWrites--; }
            }
        });
        return operation;
    }

    public void resetSeasonalData() {
        resetSeasonalDataAsync();
    }

    public void addKill(UUID playerId) {
        killCache.merge(playerId, 1, Integer::sum);
    }

    public int getKills(UUID playerId) {
        return killCache.getOrDefault(playerId, 0);
    }

    public void resetKillCache() {
        killCache.clear();
    }

    public void addKillput(UUID playerId, Player player) {
        addKillput(playerId, player.getName());
    }

    public void addKillput(UUID playerId, String playerName) {
        increment(playerId, playerName, stats -> stats.killsPut++, "kills_put", 1);
    }

    public int getKillsput(UUID playerId) {
        return stats(playerId).killsPut;
    }

    public void addDeath(UUID playerId, Player player) {
        increment(playerId, player.getName(), stats -> stats.deaths++, "deaths", 1);
    }

    public int getDeaths(UUID playerId) {
        return stats(playerId).deaths;
    }

    public void addGamePlayed(UUID playerId, Player player) {
        increment(playerId, player.getName(), stats -> stats.gamesPlayed++, "games_played", 1);
    }

    public int getGamesPlayed(UUID playerId) {
        return stats(playerId).gamesPlayed;
    }

    public void addHunterWin(UUID playerId, Player player) {
        increment(playerId, player.getName(), stats -> {
            stats.hunterWins++;
            stats.totalWins++;
        }, "hunter_wins", 1, "total_wins", 1);
    }

    public int getHunterWin(UUID playerId) {
        return stats(playerId).hunterWins;
    }

    public void addEscapeWin(UUID playerId, Player player) {
        increment(playerId, player.getName(), stats -> {
            stats.escapeWins++;
            stats.totalWins++;
        }, "escape_wins", 1, "total_wins", 1);
    }

    public int getEscapeWin(UUID playerId) {
        return stats(playerId).escapeWins;
    }

    public void saveTotalWins(UUID playerId, Player player) {
        PlayerStats stats = statsCache.computeIfAbsent(playerId, ignored -> new PlayerStats());
        synchronized (stats) {
            stats.name = player.getName();
            stats.totalWins = stats.hunterWins + stats.escapeWins;
            stats.mutationVersion++;
        }
        if (unifiedDatabase) {
            synchronized (stats) { stats.pendingWrites++; }
            enqueueWrite(() -> databaseClient.execute(
                    "UPDATE " + tableName + " SET name = ?, total_wins = hunter_wins + escape_wins WHERE uuid = ?",
                    java.util.Arrays.asList(player.getName(), playerId.toString())))
                    .whenComplete((ignored, error) -> {
                        synchronized (stats) { stats.pendingWrites--; }
                    });
        } else {
            enqueueWrite(this::saveFileSnapshotAsync);
        }
    }

    public int getTotalWins(UUID playerId) {
        return stats(playerId).totalWins;
    }

    public void addProficiency(Player player, double count) {
        UUID playerId = player.getUniqueId();
        increment(playerId, player.getName(), stats -> stats.proficiency += count, "proficiency", count);
    }

    public double getProficiency(Player player) {
        return getProficiency(player.getUniqueId());
    }

    public double getProficiency(UUID playerId) {
        return new BigDecimal(stats(playerId).proficiency).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public void addRank(UUID playerId, Player player) {
        String playerName = player.getName();
        mutate(playerId, playerName,
                stats -> stats.rank = plugin.getRankManager().getRankName(stats.proficiency), "rank");
    }

    public Map<UUID, Integer> getAllPlayerTiers() {
        List<Map.Entry<UUID, PlayerStats>> sorted = new ArrayList<>(statsCache.entrySet());
        sorted.removeIf(entry -> entry.getValue().proficiency <= 0.0);
        sorted.sort(Comparator.comparingDouble((Map.Entry<UUID, PlayerStats> entry) -> entry.getValue().proficiency).reversed());

        Map<UUID, Integer> tiers = new HashMap<>();
        int tier = 1;
        for (Map.Entry<UUID, PlayerStats> entry : sorted) tiers.put(entry.getKey(), tier++);
        return tiers;
    }

    public CompletableFuture<Map<UUID, Integer>> getAllPlayerTiersAsync() {
        if (!unifiedDatabase) return CompletableFuture.completedFuture(getAllPlayerTiers());
        return readyFuture.thenCompose(ignored -> databaseClient.query(
                        "SELECT uuid, proficiency FROM " + tableName + " WHERE proficiency > 0",
                        Collections.emptyList()))
                .thenApply(rows -> {
                    List<Map.Entry<UUID, Double>> values = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        values.add(new java.util.AbstractMap.SimpleImmutableEntry<>(
                                UUID.fromString(String.valueOf(row.get("uuid"))),
                                PlayerStats.number(row.get("proficiency")).doubleValue()));
                    }
                    values.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
                    Map<UUID, Integer> tiers = new HashMap<>();
                    int tier = 1;
                    for (Map.Entry<UUID, Double> entry : values) tiers.put(entry.getKey(), tier++);
                    return tiers;
                });
    }

    private void mutate(UUID uuid, String playerName, StatsMutation mutation, String... fields) {
        PlayerStats stats = statsCache.computeIfAbsent(uuid, ignored -> new PlayerStats());
        synchronized (stats) {
            stats.name = playerName == null ? stats.name : playerName;
            mutation.apply(stats);
            stats.mutationVersion++;
        }
        if (unifiedDatabase) {
            synchronized (stats) { stats.pendingWrites++; }
        }
        enqueueWrite(() -> {
            PlayerStats snapshot;
            synchronized (stats) {
                snapshot = stats.copy();
            }
            return unifiedDatabase ? persistMySql(uuid, snapshot, fields) : saveFileSnapshotAsync();
        }).whenComplete((ignored, error) -> {
            if (unifiedDatabase) {
                synchronized (stats) { stats.pendingWrites--; }
            }
        });
    }

    private void increment(UUID uuid, String playerName, StatsMutation mutation, Object... fieldDeltas) {
        PlayerStats stats = statsCache.computeIfAbsent(uuid, ignored -> new PlayerStats());
        synchronized (stats) {
            stats.name = playerName == null ? stats.name : playerName;
            mutation.apply(stats);
            stats.mutationVersion++;
        }

        if (!unifiedDatabase) {
            enqueueWrite(this::saveFileSnapshotAsync);
            return;
        }

        Map<String, Number> deltas = new LinkedHashMap<>();
        for (int index = 0; index < fieldDeltas.length; index += 2) {
            deltas.put((String) fieldDeltas[index], (Number) fieldDeltas[index + 1]);
        }
        synchronized (stats) { stats.pendingWrites++; }
        enqueueWrite(() -> persistIncrementMySql(uuid, playerName, deltas))
                .whenComplete((ignored, error) -> {
                    synchronized (stats) { stats.pendingWrites--; }
                });
    }

    private CompletableFuture<Integer> persistIncrementMySql(
            UUID uuid, String playerName, Map<String, Number> deltas) {
        StringBuilder columns = new StringBuilder("uuid, name");
        StringBuilder placeholders = new StringBuilder("?, ?");
        StringBuilder updates = new StringBuilder("name = VALUES(name)");
        List<Object> params = new ArrayList<>();
        params.add(uuid.toString());
        params.add(playerName == null ? "" : playerName);

        for (Map.Entry<String, Number> entry : deltas.entrySet()) {
            String field = entry.getKey();
            columns.append(", `").append(field).append('`');
            placeholders.append(", ?");
            updates.append(", `").append(field).append("` = `").append(field)
                    .append("` + VALUES(`").append(field).append("`)");
            params.add(entry.getValue());
        }

        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ") " +
                "ON DUPLICATE KEY UPDATE " + updates;
        return databaseClient.execute(sql, params);
    }

    private CompletableFuture<Integer> persistMySql(UUID uuid, PlayerStats stats, String... fields) {
        StringBuilder columns = new StringBuilder("uuid, name");
        StringBuilder placeholders = new StringBuilder("?, ?");
        StringBuilder updates = new StringBuilder("name = VALUES(name)");
        List<Object> params = new ArrayList<>();
        params.add(uuid.toString());
        params.add(stats.name);

        for (String field : fields) {
            columns.append(", `").append(field).append('`');
            placeholders.append(", ?");
            updates.append(", `").append(field).append("` = VALUES(`").append(field).append("`)");
            params.add(stats.value(field));
        }

        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ") " +
                "ON DUPLICATE KEY UPDATE " + updates;
        return databaseClient.execute(sql, params);
    }

    private CompletableFuture<Void> saveFileSnapshotAsync() {
        Map<UUID, PlayerStats> snapshot = new HashMap<>();
        for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().copy());
        }

        return CompletableFuture.runAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerStats> entry : snapshot.entrySet()) {
                String key = entry.getKey().toString();
                PlayerStats stats = entry.getValue();
                config.set(key + ".name", stats.name);
                for (String field : PERSISTED_FIELDS) config.set(key + "." + field, stats.value(field));
            }
            try {
                config.save(dataFile);
            } catch (IOException error) {
                throw new IllegalStateException("保存玩家数据失败", error);
            }
        }, fileExecutor);
    }

    private CompletableFuture<Void> enqueueWrite(Supplier<CompletableFuture<?>> operation) {
        synchronized (writeLock) {
            if (shuttingDown) return CompletableFuture.completedFuture(null);
            writeTail = writeTail.handle((ignored, previousError) -> null)
                    .thenCompose(ignored -> operation.get())
                    .handle((ignored, error) -> {
                        if (error != null) plugin.getLogger().severe("异步保存玩家数据失败: " + messageOf(error));
                        return null;
                    });
            return writeTail;
        }
    }

    public void shutdown() {
        CompletableFuture<Void> pending;
        synchronized (writeLock) {
            shuttingDown = true;
            pending = writeTail;
        }
        try {
            pending.get(5, TimeUnit.SECONDS);
        } catch (Exception error) {
            plugin.getLogger().warning("等待玩家数据写入完成时超时: " + messageOf(error));
        }
        fileExecutor.shutdown();
        try {
            if (!fileExecutor.awaitTermination(5, TimeUnit.SECONDS)) fileExecutor.shutdownNow();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            fileExecutor.shutdownNow();
        }
        if (databaseClient != null) databaseClient.close();
    }

    private PlayerStats stats(UUID uuid) {
        return statsCache.getOrDefault(uuid, PlayerStats.EMPTY);
    }

    private List<Object> safeParams(List<Object> params) {
        return params == null ? Collections.emptyList() : params;
    }

    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法 MySQL 表名: " + identifier);
        }
        return identifier;
    }

    private String messageOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    @FunctionalInterface
    private interface StatsMutation {
        void apply(PlayerStats stats);
    }

    private static final class PlayerStats {
        private static final PlayerStats EMPTY = new PlayerStats();

        private volatile String name = "";
        private volatile int killsPut;
        private volatile int deaths;
        private volatile int gamesPlayed;
        private volatile int hunterWins;
        private volatile int escapeWins;
        private volatile int totalWins;
        private volatile double proficiency;
        private volatile String rank = "";
        private long mutationVersion;
        private int pendingWrites;

        private static PlayerStats fromRow(Map<String, Object> row) {
            PlayerStats stats = new PlayerStats();
            stats.name = valueOrEmpty(row.get("name"));
            stats.killsPut = number(row.get("kills_put")).intValue();
            stats.deaths = number(row.get("deaths")).intValue();
            stats.gamesPlayed = number(row.get("games_played")).intValue();
            stats.hunterWins = number(row.get("hunter_wins")).intValue();
            stats.escapeWins = number(row.get("escape_wins")).intValue();
            stats.totalWins = number(row.get("total_wins")).intValue();
            stats.proficiency = number(row.get("proficiency")).doubleValue();
            stats.rank = valueOrEmpty(row.get("rank"));
            return stats;
        }

        private static PlayerStats fromConfig(YamlConfiguration config, String key) {
            PlayerStats stats = new PlayerStats();
            stats.name = config.getString(key + ".name", "");
            stats.killsPut = config.getInt(key + ".kills_put");
            stats.deaths = config.getInt(key + ".deaths");
            stats.gamesPlayed = config.getInt(key + ".games_played");
            stats.hunterWins = config.getInt(key + ".hunter_wins");
            stats.escapeWins = config.getInt(key + ".escape_wins");
            stats.totalWins = config.getInt(key + ".total_wins", stats.hunterWins + stats.escapeWins);
            stats.proficiency = config.getDouble(key + ".proficiency");
            stats.rank = config.getString(key + ".rank", "");
            return stats;
        }

        private Object value(String field) {
            switch (field) {
                case "kills_put": return killsPut;
                case "deaths": return deaths;
                case "games_played": return gamesPlayed;
                case "hunter_wins": return hunterWins;
                case "escape_wins": return escapeWins;
                case "total_wins": return totalWins;
                case "proficiency": return proficiency;
                case "rank": return rank;
                default: throw new IllegalArgumentException("未知玩家数据字段: " + field);
            }
        }

        private PlayerStats copy() {
            PlayerStats copy = new PlayerStats();
            copy.name = name;
            copy.killsPut = killsPut;
            copy.deaths = deaths;
            copy.gamesPlayed = gamesPlayed;
            copy.hunterWins = hunterWins;
            copy.escapeWins = escapeWins;
            copy.totalWins = totalWins;
            copy.proficiency = proficiency;
            copy.rank = rank;
            copy.mutationVersion = mutationVersion;
            return copy;
        }

        private void replaceWith(PlayerStats persisted) {
            name = persisted.name;
            killsPut = persisted.killsPut;
            deaths = persisted.deaths;
            gamesPlayed = persisted.gamesPlayed;
            hunterWins = persisted.hunterWins;
            escapeWins = persisted.escapeWins;
            totalWins = persisted.totalWins;
            proficiency = persisted.proficiency;
            rank = persisted.rank;
        }

        private PlayerStats mergePersistedBase(PlayerStats persisted) {
            synchronized (this) {
                killsPut += persisted.killsPut;
                deaths += persisted.deaths;
                gamesPlayed += persisted.gamesPlayed;
                hunterWins += persisted.hunterWins;
                escapeWins += persisted.escapeWins;
                totalWins = hunterWins + escapeWins;
                proficiency += persisted.proficiency;
                if (name.isEmpty()) name = persisted.name;
                if (rank.isEmpty()) rank = persisted.rank;
                return this;
            }
        }

        private static Number number(Object value) {
            if (value instanceof Number) return (Number) value;
            if (value == null) return 0;
            return new BigDecimal(String.valueOf(value));
        }

        private static String valueOrEmpty(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }
}
