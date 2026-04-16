package com.ntdat.cache.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class VersionInvalidateSubscriber implements MessageListener {

    private final VersionLocalCache localCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String vKey = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("Received version invalidate event for key: {}", vKey);
        localCache.invalidate(vKey);
    }
}
