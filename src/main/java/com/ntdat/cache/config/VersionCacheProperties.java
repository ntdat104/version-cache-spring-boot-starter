package com.ntdat.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "version-cache")
public class VersionCacheProperties {
    /**
     * TTL local cache (seconds)
     */
    private int localTtlSeconds = 30;

    /**
     * Max entries local cache
     */
    private int maxSize = 50000;

    /**
     * TTL version key in Redis (days)
     */
    private int versionKeyTtlDays = 3;

    /**
     * Enable logging
     */
    private boolean enableLogging = true;

    /**
     * Enable cache (kill switch)
     */
    private boolean enabled = true;
}
