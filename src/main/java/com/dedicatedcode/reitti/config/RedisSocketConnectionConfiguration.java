package com.dedicatedcode.reitti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSocketConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisSocketConnectionConfiguration {

    @Bean
    @ConditionalOnPropertyNotEmpty("reitti.redis.socket-path")
    public LettuceConnectionFactory redisConnectionFactory(@Value("${reitti.redis.socket-path}") String socketPath,
                                                          @Value("${spring.data.redis.username:}") String username,
                                                          @Value("${spring.data.redis.password:}") String password,
                                                          @Value("${spring.data.redis.database:0}") int database) {
        RedisSocketConfiguration configuration = new RedisSocketConfiguration(socketPath);
        if (!username.isBlank()) {
            configuration.setUsername(username);
        }
        if (!password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        configuration.setDatabase(database);
        return new LettuceConnectionFactory(configuration);
    }
}
