package com.ntdat.cache.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntdat.cache.annotation.BumpVersion;
import com.ntdat.cache.annotation.VersionCache;
import com.ntdat.cache.cache.VersionLocalCache;
import com.ntdat.cache.config.RedisPubSubConfiguration;
import com.ntdat.cache.config.VersionCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class VersionCacheAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final VersionLocalCache localCache;
    private final VersionCacheProperties props;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();

    // ================= READ =================
    @Around("@annotation(autoCache)")
    public Object handleRead(ProceedingJoinPoint joinPoint, VersionCache autoCache) throws Throwable {

        if (!props.isEnabled()) {
            return joinPoint.proceed();
        }

        Object userId = parseSpel(joinPoint, autoCache.userId());
        String entity = autoCache.entity();
        String vKey = buildVersionKey(entity, userId);

        try {
            // 🔥 1. local cache
            String version = getVersion(vKey);

            String extraKey = buildExtraKey(joinPoint, autoCache.extraKeys());
            String finalKey = buildDataKey(entity, userId, extraKey, version);

            // 🔥 3. data cache
            String cached = redisTemplate.opsForValue().get(finalKey);

            if (cached != null) {
                logHit("DATA_CACHE_HIT", finalKey);
                return deserialize(cached, joinPoint);
            }

            // 4. Single Flight if Cache Miss
            logMiss(finalKey);

            Object lock = keyLocks.computeIfAbsent(finalKey, k -> new Object());

            synchronized (lock) {
                try {
                    String retryCache = redisTemplate.opsForValue().get(finalKey);
                    if (retryCache != null) {
                        logHit("SINGLE_FLIGHT_RECOVERY", finalKey);
                        return deserialize(retryCache, joinPoint);
                    }

                    log.info("[SINGLE_FLIGHT] Only one thread fetching DB for: {}", finalKey);

                    Object result = joinPoint.proceed();

                    if (result != null) {
                        redisTemplate.opsForValue().set(
                                finalKey,
                                objectMapper.writeValueAsString(result),
                                autoCache.ttl(),
                                autoCache.unit()
                        );
                    }
                    return result;
                } finally {
                    keyLocks.remove(finalKey);
                }
            }

        } catch (Exception ex) {
            // 🔥 fallback
            logFallback(ex);
            return joinPoint.proceed();
        }
    }

    // ================= WRITE =================
    @AfterReturning("@annotation(bump)")
    public void handleWrite(JoinPoint joinPoint, BumpVersion bump) {

        if (!props.isEnabled()) return;

        Object userId = parseSpel(joinPoint, bump.userId());
        String entity = bump.entity();
        String vKey = buildVersionKey(entity, userId);

        try {
            Long newVersion = redisTemplate.opsForValue().increment(vKey);
            redisTemplate.expire(vKey, props.getVersionKeyTtlDays(), TimeUnit.DAYS);

            redisTemplate.convertAndSend(RedisPubSubConfiguration.VERSION_INVALIDATE_CHANNEL, vKey);

            log.info("[CACHE_BUMP] key={}, version={}", vKey, newVersion);

        } catch (Exception ex) {
            logFallback(ex);
        }
    }

    // ================= LOGGING =================

    private void logHit(String type, String key) {
        if (props.isEnableLogging()) {
            log.info("[CACHE_{}] key={}", type, key);
        }
    }

    private void logMiss(String key) {
        if (props.isEnableLogging()) {
            log.info("[CACHE_MISS] key={}", key);
        }
    }

    private void logFallback(Exception ex) {
        log.error("[CACHE_FALLBACK] Redis error → fallback to DB", ex);
    }

    // ================= HELPER =================

    private String getVersion(String vKey) {
        String version = localCache.get(vKey);
        if (version != null) {
            logHit("LOCAL_VERSION_HIT", vKey);
            return version;
        }

        version = redisTemplate.opsForValue().get(vKey);
        if (version == null) version = "0";

        localCache.put(vKey, version);
        logHit("REDIS_VERSION_HIT", vKey);
        return version;
    }

    private Object deserialize(String value, JoinPoint joinPoint) throws Exception {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return objectMapper.readValue(value, signature.getReturnType());
    }

    private String buildVersionKey(String entity, Object userId) {
        return "version:" + entity + ":" + userId;
    }

    private String buildDataKey(String entity, Object userId, String extraKey, String version) {
        return String.format("%s:data:%s:%s:v%s", entity, userId, extraKey, version);
    }

    private String buildExtraKey(JoinPoint jp, String[] expressions) {
        if (expressions == null || expressions.length == 0) return "default";

        StringJoiner joiner = new StringJoiner("_");

        for (String expr : expressions) {
            Object value = parseSpel(jp, expr);
            joiner.add(value != null ? value.toString() : "null");
        }

        return joiner.toString();
    }

    private Object parseSpel(JoinPoint jp, String expression) {
        if (expression == null || expression.isEmpty()) return "default";

        MethodSignature sig = (MethodSignature) jp.getSignature();

        EvaluationContext context = new MethodBasedEvaluationContext(
                jp.getTarget(),
                sig.getMethod(),
                jp.getArgs(),
                nameDiscoverer
        );

        return parser.parseExpression(expression).getValue(context);
    }
}
