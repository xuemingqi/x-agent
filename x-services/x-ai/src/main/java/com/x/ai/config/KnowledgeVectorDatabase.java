package com.x.ai.config;

import com.x.ai.properties.KnowledgeVectorProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 独立向量库连接池不注册为 DataSource Bean，保留现有 MySQL 自动配置。
 */
@Component
public class KnowledgeVectorDatabase implements AutoCloseable {

    private final HikariDataSource pool;

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    public KnowledgeVectorDatabase(KnowledgeVectorProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("knowledge-vector");
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10_000);
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("connectTimeout", "10");
        config.addDataSourceProperty("socketTimeout", "30");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        pool = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(pool);
        jdbcTemplate.setQueryTimeout(30);
        transactionTemplate = new TransactionTemplate(new JdbcTransactionManager(pool));
    }

    public JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    public TransactionTemplate transaction() {
        return transactionTemplate;
    }

    @Override
    public void close() {
        pool.close();
    }
}
