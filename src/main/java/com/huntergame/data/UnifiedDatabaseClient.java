package com.huntergame.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface UnifiedDatabaseClient extends AutoCloseable {

    CompletableFuture<Integer> execute(String sql, List<Object> params);

    CompletableFuture<List<Map<String, Object>>> query(String sql, List<Object> params);

    CompletableFuture<Void> transaction(TransactionAction action);

    @Override
    default void close() {
    }

    @FunctionalInterface
    interface TransactionAction {
        void execute(Transaction transaction) throws Exception;
    }

    interface Transaction {
        int execute(String sql, List<Object> params);
    }
}
