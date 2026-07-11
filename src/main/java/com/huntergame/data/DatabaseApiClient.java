package com.huntergame.data;

import com.xigua.database.api.DatabaseApi;
import com.xigua.database.api.DatabaseType;
import com.xigua.database.api.mysql.MySqlClient;
import com.xigua.database.api.mysql.MySqlRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class DatabaseApiClient implements UnifiedDatabaseClient {

    private final MySqlClient mysql;

    private DatabaseApiClient(MySqlClient mysql) {
        this.mysql = mysql;
    }

    static DatabaseApiClient create() {
        DatabaseApi api = DatabaseApi.getInstance();
        if (api == null || !api.isEnabled(DatabaseType.MYSQL) || api.mysql() == null) return null;
        return new DatabaseApiClient(api.mysql());
    }

    @Override
    public CompletableFuture<Integer> execute(String sql, List<Object> params) {
        return mysql.execute(sql, params);
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> query(String sql, List<Object> params) {
        return mysql.query(sql, params).thenApply(rows -> {
            List<Map<String, Object>> result = new ArrayList<>(rows.size());
            for (MySqlRow row : rows) result.add(new HashMap<>(row.asMap()));
            return result;
        });
    }

    @Override
    public CompletableFuture<Void> transaction(TransactionAction action) {
        return mysql.transaction(transaction -> action.execute(transaction::execute));
    }
}
