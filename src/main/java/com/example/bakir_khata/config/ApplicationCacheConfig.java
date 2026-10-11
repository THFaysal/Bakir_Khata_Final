package com.example.bakir_khata.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class ApplicationCacheConfig {
    public static final String OPEN_BROADCASTS = "communityOpenBroadcasts";
    public static final String BORROWER_RATINGS = "borrowerRatings";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            new CaffeineCache(OPEN_BROADCASTS, Caffeine.newBuilder()
                .maximumSize(1).expireAfterWrite(Duration.ofSeconds(15)).recordStats().build()),
            new CaffeineCache(BORROWER_RATINGS, Caffeine.newBuilder()
                .maximumSize(500).expireAfterWrite(Duration.ofSeconds(30)).recordStats().build())
        ));
        return manager;
    }
}
