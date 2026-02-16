package com.dedicatedcode.reitti.repository.h3;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class NamedParameterH3JdbcProvider
{
    @Bean(name = "namedParameterH3JdbcTemplate")
    public NamedParameterJdbcTemplate namedParameterH3JdbcTemplate(@Qualifier("h3JdbcTemplate") JdbcTemplate jdbcTemplate)
    {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }
}
