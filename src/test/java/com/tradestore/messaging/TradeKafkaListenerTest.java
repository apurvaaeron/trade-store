package com.tradestore.messaging;

import static com.tradestore.TestTrades.TODAY;
import static com.tradestore.TestTrades.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradestore.audit.Channel;
import com.tradestore.config.TradeStoreProperties;
import com.tradestore.domain.LowerVersionException;
import com.tradestore.domain.RejectionReason;
import com.tradestore.domain.TradeRequest;
import com.tradestore.service.TradeIngestionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class TradeKafkaListenerTest {

    private static final Instant NOW = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC);
    private final TradeIngestionService ingestion = mock(TradeIngestionService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
    private final TradeStoreProperties properties = new TradeStoreProperties(ZoneOffset.UTC,
            new TradeStoreProperties.Topics("in", "rejected", "dlt", 1),
            new TradeStoreProperties.Expiry("0 0 0 * * *", false));
    private final TradeKafkaListener listener =
            new TradeKafkaListener(ingestion, template, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    private final TradePublisher publisher = new TradePublisher(template, properties);

    @Test
    void ingestsTradeFromKafka() {
        TradeRequest request = trade("T1", 1, TODAY);

        listener.onTrade(request);

        verify(ingestion).ingest(request, Channel.KAFKA);
        verify(template, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishesBusinessRejectionToRejectedTopic() {
        TradeRequest request = trade("T2", 1, TODAY);
        when(ingestion.ingest(request, Channel.KAFKA)).thenThrow(new LowerVersionException("T2", 1, 2));

        listener.onTrade(request);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).send(eq("rejected"), eq("T2"), captor.capture());
        TradeRejection rejection = (TradeRejection) captor.getValue();
        assertThat(rejection.reason()).isEqualTo(RejectionReason.LOWER_VERSION);
        assertThat(rejection.version()).isEqualTo(1);
        assertThat(rejection.trade()).isEqualTo(request);
        assertThat(rejection.rejectedAt()).isEqualTo(NOW);
    }

    @Test
    void technicalFailuresPropagateToTheContainerErrorHandler() {
        TradeRequest request = trade("T3", 1, TODAY);
        when(ingestion.ingest(request, Channel.KAFKA)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> listener.onTrade(request)).isInstanceOf(IllegalStateException.class);
        verify(template, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publisherKeysMessagesByTradeId() {
        TradeRequest request = trade("T7", 1, TODAY);

        publisher.publish(request);

        verify(template).send("in", "T7", request);
    }
}
