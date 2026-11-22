package com.tradestore.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    @Bean
    public NewTopic inboundTopic(TradeStoreProperties properties) {
        return TopicBuilder.name(properties.topics().inbound()).partitions(properties.topics().partitions()).build();
    }

    @Bean
    public NewTopic rejectedTopic(TradeStoreProperties properties) {
        return TopicBuilder.name(properties.topics().rejected()).partitions(properties.topics().partitions()).build();
    }

    @Bean
    public NewTopic deadLetterTopic(TradeStoreProperties properties) {
        return TopicBuilder.name(properties.topics().deadLetter()).partitions(1).build();
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public DefaultKafkaProducerFactoryCustomizer kafkaProducerFactoryCustomizer() {
        return factory -> {
            Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
            serializers.put(byte[].class, new ByteArraySerializer());
            serializers.put(Object.class, new JacksonJsonSerializer<>());
            factory.setValueSerializer((Serializer) new DelegatingByTypeSerializer(serializers, true));
        };
    }

    /**
     * Technical failures (DB down, poison messages) are retried, then parked on the dead-letter topic so a
     * single bad record never blocks a partition. Business rejections are handled in the listener instead.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template, TradeStoreProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(properties.topics().deadLetter(), -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3));
    }
}
