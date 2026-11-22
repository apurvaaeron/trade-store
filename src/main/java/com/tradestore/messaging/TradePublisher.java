package com.tradestore.messaging;

import com.tradestore.config.TradeStoreProperties;
import com.tradestore.domain.TradeRequest;
import com.tradestore.messaging.TradePublishException.Outcome;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.KafkaException;
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

    /** Waits up to the configured timeout, including any metadata wait inside send, for Kafka to confirm (acks=all). */
    public void publishAndAwait(TradeRequest request) {
        long deadline = System.nanoTime() + properties.async().publishTimeout().toNanos();
        CompletableFuture<SendResult<String, Object>> sent;
        try {
            sent = publish(request);
        } catch (KafkaException e) {
            throw new TradePublishException(request.tradeId(), Outcome.FAILED, e);
        }
        try {
            sent.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new TradePublishException(request.tradeId(), Outcome.UNKNOWN, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TradePublishException(request.tradeId(), Outcome.UNKNOWN, e);
        } catch (ExecutionException e) {
            throw new TradePublishException(request.tradeId(), Outcome.FAILED, e.getCause());
        }
    }
}
