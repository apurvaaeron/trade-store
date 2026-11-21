package com.tradestore.messaging;

import com.tradestore.config.TradeStoreProperties;
import com.tradestore.domain.TradeRequest;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/** Publishes trades to the inbound topic, keyed by trade id to preserve per-trade ordering. */
@Component
public class TradePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TradeStoreProperties properties;

    public TradePublisher(KafkaTemplate<String, Object> kafkaTemplate, TradeStoreProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public CompletableFuture<SendResult<String, Object>> publish(TradeRequest request) {
        return kafkaTemplate.send(properties.topics().inbound(), request.tradeId(), request);
    }
}
