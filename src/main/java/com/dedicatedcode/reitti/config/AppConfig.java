package com.dedicatedcode.reitti.config;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class AppConfig {
    @Bean
    public GeometryFactory geometryFactory() {
        return new GeometryFactory(new PrecisionModel(), 4326);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(RedisConnectionFactory connectionFactory) {
        return (builder) -> {
            builder.cacheWriter(
                    RedisCacheWriter.nonLockingRedisCacheWriter(
                            connectionFactory,
                            BatchStrategies.scan(1000)
                    )
            );
        };
    }
    @Bean
    public static BeanFactoryPostProcessor rqueuePropertyOverride() {
        return beanFactory -> {
            ConfigurableEnvironment env = (ConfigurableEnvironment) beanFactory.getBean(Environment.class);

            // 1. Get the prefix (default to empty string if not set)
            String prefix = env.getProperty("spring.cache.redis.key-prefix", "");

            // 2. If a prefix exists, calculate the new version key
            if (!prefix.isEmpty()) {
                String newVersionKey = prefix + "__rq::version";

                // 3. Inject this back into the environment properties
                Map<String, Object> map = new HashMap<>();
                map.put("rqueue.version.key", newVersionKey);

                env.getPropertySources().addFirst(new MapPropertySource("rqueueOverride", map));
            }
        };
    }
}
