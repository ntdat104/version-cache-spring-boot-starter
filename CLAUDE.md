# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot starter library providing **version-based two-tier caching** using Redis (distributed version store + data cache) and Caffeine (local in-process version cache). The key insight: instead of invalidating cache entries directly, each cache namespace has a "version" in Redis that gets incremented on writes, making stale local version entries automatically invalid on next read.

## Build Commands

```bash
# Build the JAR
mvn package

# Install to local Maven repo (for use in other projects)
mvn install

# Clean build
mvn clean package

# Skip tests
mvn install -DskipTests
```

No Maven wrapper (`mvnw`) is present — use a locally installed `mvn`. Java 21 required.

## Architecture

### Cache Flow (Read path via `@VersionCache`)

1. Check Caffeine (`VersionLocalCache`) for the version key → if hit, use it
2. Otherwise fetch version from Redis (`version:{entity}:{userId}`) → store in Caffeine
3. Build data key: `{entity}:data:{userId}:{extraKeys}:v{version}`
4. Check Redis for data at that key → if hit, deserialize via Jackson and return
5. On miss: call the real method, store result in Redis with the configured TTL
6. On any Redis/cache error: fall back silently to the real method

### Cache Invalidation (Write path via `@BumpVersion`)

Runs `INCR` on `version:{entity}:{userId}` in Redis (after method returns), then updates Caffeine with the new version. This makes all previously cached data keys for that entity+user effectively unreachable.

### Key Classes

| Class | Role |
|---|---|
| `VersionCacheAspect` | AOP `@Around`/`@AfterReturning` advice; all cache logic lives here |
| `VersionLocalCache` | Caffeine wrapper; stores only version strings (not data); adds random jitter to TTL to avoid thundering herd |
| `VersionCacheProperties` | `@ConfigurationProperties(prefix = "version-cache")`; kill switch + tuning |
| `VersionCacheAutoConfiguration` | Enables `VersionCacheProperties`; beans are `@Component`-scanned |

### Annotations

**`@VersionCache`** — on read methods:
- `entity`: cache namespace (e.g. `"order"`)
- `userId`: SpEL expression evaluated against method args (e.g. `"#id"`, `"#req.userId"`)
- `extraKeys`: additional SpEL expressions joined with `_` to differentiate cache entries within the same entity+user
- `ttl` / `unit`: Redis data key TTL (default: 1 minute)

**`@BumpVersion`** — on write methods:
- `entity` + `userId`: same SpEL semantics as above; identifies which version key to increment

### Configuration Properties (`version-cache.*`)

| Property | Default | Description |
|---|---|---|
| `enabled` | `true` | Kill switch — disables all caching when false |
| `enable-logging` | `true` | Log cache hits/misses/bumps at INFO level |
| `local-ttl-seconds` | `30` | Caffeine version cache TTL (+ 0–9s random jitter) |
| `max-size` | `50000` | Max entries in Caffeine cache |
| `version-key-ttl-days` | `3` | Redis version key TTL |

### Redis Key Schema

- Version key: `version:{entity}:{userId}`
- Data key: `{entity}:data:{userId}:{extraKey}:v{version}`

## Testing

No tests exist yet. To add them, declare `spring-boot-starter-test` in `pom.xml`. Single test commands when tests exist:
```bash
mvn test -Dtest=MyTestClass
mvn test -Dtest=MyTestClass#myMethod
```
