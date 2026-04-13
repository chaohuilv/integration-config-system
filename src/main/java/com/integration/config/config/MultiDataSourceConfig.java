package com.integration.config.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源配置
 * 三个独立 H2 数据库：
 *   - config 库：存储接口配置 (ApiConfig)
 *   - log    库：存储调用日志 (InvokeLog)
 *   - token  库：存储Token缓存 (TokenCacheEntry)
 *
 * 每个库单独配置 EntityManagerFactory + TransactionManager + @EnableJpaRepositories
 */
@Configuration
@EnableTransactionManagement
public class MultiDataSourceConfig {

    // ================================================================
    // Config 数据源（接口配置）
    // ================================================================

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.integration.config.repository.config",
            entityManagerFactoryRef = "configEntityManagerFactory",
            transactionManagerRef = "configTransactionManager"
    )
    static class ConfigDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource-config")
        public DataSourceProperties configDataSourceProperties() {
            return new DataSourceProperties();
        }

        @Bean
        @Primary
        public DataSource configDataSource() {
            return configDataSourceProperties()
                    .initializeDataSourceBuilder()
                    .build();
        }

        @Bean
        @Primary
        public LocalContainerEntityManagerFactoryBean configEntityManagerFactory(
                @Qualifier("configDataSource") DataSource dataSource) {
            return buildEntityManagerFactory(dataSource,
                    "com.integration.config.entity.config");
        }

        @Bean
        @Primary
        public PlatformTransactionManager configTransactionManager(
                @Qualifier("configEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
            return new JpaTransactionManager(emf.getObject());
        }
    }

    // ================================================================
    // Log 数据源（调用日志）
    // ================================================================

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.integration.config.repository.log",
            entityManagerFactoryRef = "logEntityManagerFactory",
            transactionManagerRef = "logTransactionManager"
    )
    static class LogDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource-log")
        public DataSourceProperties logDataSourceProperties() {
            return new DataSourceProperties();
        }

        @Bean
        public DataSource logDataSource() {
            return logDataSourceProperties()
                    .initializeDataSourceBuilder()
                    .build();
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean logEntityManagerFactory(
                @Qualifier("logDataSource") DataSource dataSource) {
            return buildEntityManagerFactory(dataSource,
                    "com.integration.config.entity.log");
        }

        @Bean
        public PlatformTransactionManager logTransactionManager(
                @Qualifier("logEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
            return new JpaTransactionManager(emf.getObject());
        }
    }

    // ================================================================
    // Token 数据源（Token缓存）
    // ================================================================

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.integration.config.repository.token",
            entityManagerFactoryRef = "tokenEntityManagerFactory",
            transactionManagerRef = "tokenTransactionManager"
    )
    static class TokenDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource-token")
        public DataSourceProperties tokenDataSourceProperties() {
            return new DataSourceProperties();
        }

        @Bean
        public DataSource tokenDataSource() {
            return tokenDataSourceProperties()
                    .initializeDataSourceBuilder()
                    .build();
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean tokenEntityManagerFactory(
                @Qualifier("tokenDataSource") DataSource dataSource) {
            return buildEntityManagerFactory(dataSource,
                    "com.integration.config.entity.token");
        }

        @Bean
        public PlatformTransactionManager tokenTransactionManager(
                @Qualifier("tokenEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
            return new JpaTransactionManager(emf.getObject());
        }
    }

    // ================================================================
    // 通用方法
    // ================================================================

    private static LocalContainerEntityManagerFactoryBean buildEntityManagerFactory(
            DataSource dataSource, String... basePackages) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(basePackages);

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(false);
        vendorAdapter.setGenerateDdl(true);
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("hibernate.format_sql", false);
        em.setJpaPropertyMap(props);

        return em;
    }
}
