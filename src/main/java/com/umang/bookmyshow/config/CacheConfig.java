package com.umang.bookmyshow.config;

import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@code @Cacheable} on CatalogService. A simple in-memory cache manager is fine
 * for the portfolio scope; swap for Redis/Caffeine in production for TTL + eviction.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(List.of("movies", "shows", "show_seats").toArray(new String[0]));
    }
}
