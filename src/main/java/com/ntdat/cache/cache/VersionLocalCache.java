package com.ntdat.cache.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ntdat.cache.config.VersionCacheProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class VersionLocalCache {

    private final Cache<String, String> cache;

    public VersionLocalCache(VersionCacheProperties props) {
        int ttl = props.getLocalTtlSeconds();

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl + ThreadLocalRandom.current().nextInt(10), TimeUnit.SECONDS)
                .maximumSize(props.getMaxSize())
                .build();
    }

    public String get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, String value) {
        cache.put(key, value);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
