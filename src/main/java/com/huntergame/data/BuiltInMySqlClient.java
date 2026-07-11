package com.huntergame.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class BuiltInMySqlClient implements UnifiedDatabaseClient {

    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    BuiltInMySqlClient(FileConfiguration config) {
        int poolSize = Math.max(2, config.getInt("database.pool_size", 5));
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?serverTimezone=%s&useSSL=%b&allowPublicKeyRetrieval=true&characterEncoding=utf-8&rewriteBatchedStatements=true",
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.name", "minecraft"),
                config.getString("database.timezone", "UTC"),
                config.getBoolean("database.use_ssl", false)
        );

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setUsername(config.getString("database.username", "root"));
        hikari.setPassword(config.getString("database.password", ""));
        hikari.setMaximumPoolSize(poolSize);
        hikari.setMinimumIdle(Math.min(2, poolSize));
        hikari.setConnectionTimeout(config.getLong("database.connection_timeout_ms", 5000L));
        hikari.setInitializationFailTimeout(-1L);
        hikari.setPoolName("HunterGame-MySQL");
        dataSource = new HikariDataSource(hikari);

        executor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "HunterGame-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletableFuture<Integer> execute(String sql, List<Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                return statement.executeUpdate();
            } catch (SQLException error) {
                throw new CompletionException(error);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> query(String sql, List<Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return rows(resultSet);
                }
            } catch (SQLException error) {
                throw new CompletionException(error);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> transaction(TransactionAction action) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    action.execute((sql, params) -> executeInTransaction(connection, sql, params));
                    connection.commit();
                } catch (Exception error) {
                    connection.rollback();
                    throw new CompletionException(error);
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException error) {
                throw new CompletionException(error);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        dataSource.close();
    }

    private int executeInTransaction(Connection connection, String sql, List<Object> params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException error) {
            throw new CompletionException(error);
        }
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int index = 0; index < params.size(); index++) {
            statement.setObject(index + 1, params.get(index));
        }
    }

    private static List<Map<String, Object>> rows(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                row.put(metadata.getColumnLabel(column), resultSet.getObject(column));
            }
            rows.add(row);
        }
        return rows;
    }
}
