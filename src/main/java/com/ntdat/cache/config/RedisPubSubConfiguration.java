package com.ntdat.cache.config;

import com.ntdat.cache.cache.VersionInvalidateSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

public class RedisPubSubConfiguration {

    public static final String VERSION_INVALIDATE_CHANNEL = "version:invalidate";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            VersionInvalidateSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                new MessageListenerAdapter(subscriber),
                new ChannelTopic(VERSION_INVALIDATE_CHANNEL)
        );
        return container;
    }
}
