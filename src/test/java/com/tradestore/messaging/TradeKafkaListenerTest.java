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
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

class TradeKafkaListenerTest {

    private static final Instant NOW = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC);
    private final TradeIngestionService ingestion = mock(TradeIngestionService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
    private final TradeStoreProperties properties = new TradeStoreProperties(ZoneOffset.UTC,
            new TradeStoreProperties.Topics("in", "rejected", "dlt", 1),
            new TradeStoreProperties.Expiry("0 0 0 * * *", false),
            new TradeStoreProperties.Async(Duration.ofMillis(50)));
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
        when(template.send(eq("rejected"), eq("T2"), any())).thenReturn(CompletableFuture.completedFuture(null));

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
    void rejectionPublicationFailurePropagatesToTheContainerErrorHandler() {
        TradeRequest request = trade("T4", 1, TODAY);
        when(ingestion.ingest(request, Channel.KAFKA)).thenThrow(new LowerVersionException("T4", 1, 2));
        when(template.send(eq("rejected"), eq("T4"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        assertThatThrownBy(() -> listener.onTrade(request))
                .isInstanceOf(KafkaException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
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

    @Test
    void publishAndAwaitReturnsOnceKafkaConfirms() {
        TradeRequest request = trade("T8", 1, TODAY);
        when(template.send("in", "T8", request)).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishAndAwait(request);

        verify(template).send("in", "T8", request);
    }

    @Test
    void publishAndAwaitReportsUnknownOutcomeWhenKafkaDoesNotConfirmInTime() {
        TradeRequest request = trade("T8", 1, TODAY);
        when(template.send("in", "T8", request)).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> publisher.publishAndAwait(request))
                .isInstanceOfSatisfying(TradePublishException.class, e -> {
                    assertThat(e.getOutcome()).isEqualTo(TradePublishException.Outcome.UNKNOWN);
                    assertThat(e.getTradeId()).isEqualTo("T8");
                });
    }

    @Test
    void publishAndAwaitReportsFailureWhenKafkaReturnsAnError() {
        TradeRequest request = trade("T8", 1, TODAY);
        when(template.send("in", "T8", request))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker error")));

        assertThatThrownBy(() -> publisher.publishAndAwait(request))
                .isInstanceOfSatisfying(TradePublishException.class,
                        e -> assertThat(e.getOutcome()).isEqualTo(TradePublishException.Outcome.FAILED))
                .hasRootCauseMessage("broker error");
    }

    @Test
    void publishAndAwaitReportsFailureWhenSendFailsImmediately() {
        TradeRequest request = trade("T8", 1, TODAY);
        when(template.send("in", "T8", request)).thenThrow(new KafkaException("metadata timeout"));

        assertThatThrownBy(() -> publisher.publishAndAwait(request))
                .isInstanceOfSatisfying(TradePublishException.class,
                        e -> assertThat(e.getOutcome()).isEqualTo(TradePublishException.Outcome.FAILED));
    }
}
