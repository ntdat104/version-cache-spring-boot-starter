package com.ntdat.cache.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VersionCacheProperties.class)
public class VersionCacheAutoConfiguration {
}