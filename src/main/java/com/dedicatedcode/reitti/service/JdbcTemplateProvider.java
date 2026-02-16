package com.dedicatedcode.reitti.service;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class JdbcTemplateProvider
{
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource mainDataSource()
    {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public JdbcTemplate mainJdbcTemplate(DataSource mainDataSource) {
        return new JdbcTemplate(mainDataSource);
    }

    @Bean(name ="h3DataSource")
    @ConfigurationProperties(prefix="reitti.h3.datasource")
    @ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
    public DataSource h3DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "h3JdbcTemplate")
    @ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
    public JdbcTemplate h3JdbcTemplate(@Qualifier("h3DataSource") DataSource h3DataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(h3DataSource)
            .locations("db.h3.migration")
            .load();
        flyway.migrate();

        return new JdbcTemplate(h3DataSource);
    }

}
