package com.tradestore.messaging;

import com.tradestore.audit.Channel;
import com.tradestore.config.TradeStoreProperties;
import com.tradestore.domain.TradeRejectedException;
import com.tradestore.domain.TradeRequest;
import com.tradestore.service.TradeIngestionService;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes trades keyed by trade id, so all versions of one trade land on one partition in order.
 * Business rejections are final (no retry) and go to the rejected topic; anything else is left to the
 * container error handler (retry, then dead-letter topic).
 */
@Component
public class TradeKafkaListener {

    private final TradeIngestionService ingestionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TradeStoreProperties properties;
    private final Clock clock;

    public TradeKafkaListener(TradeIngestionService ingestionService, KafkaTemplate<String, Object> kafkaTemplate,
                              TradeStoreProperties properties, Clock clock) {
        this.ingestionService = ingestionService;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @KafkaListener(topics = "${tradestore.topics.inbound:trades.inbound}", groupId = "${spring.kafka.consumer.group-id:trade-store}")
    public void onTrade(TradeRequest request) {
        try {
            ingestionService.ingest(request, Channel.KAFKA);
        } catch (TradeRejectedException e) {
            TradeRejection rejection = new TradeRejection(e.getTradeId(), request.version(), e.getReason(),
                    e.getMessage(), request, clock.instant());
            try {
                kafkaTemplate.send(properties.topics().rejected(), e.getTradeId(), rejection).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new KafkaException("Interrupted while publishing trade rejection", ex);
            } catch (ExecutionException | TimeoutException ex) {
                Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                throw new KafkaException("Failed to publish trade rejection", cause);
            }
        }
    }
}
