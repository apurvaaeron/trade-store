package com.tradestore.audit;

import static com.tradestore.TestTrades.TODAY;
import static com.tradestore.TestTrades.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradestore.audit.TradeAuditEvent.Outcome;
import com.tradestore.domain.MaturityDateInPastException;
import com.tradestore.domain.RejectionReason;
import com.tradestore.domain.TradeRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TradeAuditServiceTest {

    private static final Instant NOW = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC);
    private final TradeAuditRepository repository = mock(TradeAuditRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TradeAuditService service =
            new TradeAuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);

    @Test
    void recordsAcceptedTrade() {
        TradeRequest request = trade("T1", 1, TODAY);

        service.accepted(request, Channel.REST);

        TradeAuditEvent event = captured();
        assertThat(event.tradeId()).isEqualTo("T1");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.channel()).isEqualTo(Channel.REST);
        assertThat(event.outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(event.reason()).isNull();
        assertThat(event.payload()).isEqualTo(request);
        assertThat(event.receivedAt()).isEqualTo(NOW);
    }

    @Test
    void recordsRejectionWithReason() {
        TradeRequest request = trade("T1", 1, TODAY.minusDays(1));

        service.rejected(request, Channel.KAFKA, new MaturityDateInPastException("T1", TODAY.minusDays(1), TODAY));

        TradeAuditEvent event = captured();
        assertThat(event.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(event.reason()).isEqualTo(RejectionReason.MATURITY_DATE_IN_PAST);
        assertThat(event.message()).contains("maturity date");
    }

    @Test
    void auditFailureDoesNotPropagate() {
        when(repository.save(any())).thenThrow(new IllegalStateException("mongo down"));

        assertThatCode(() -> service.accepted(trade("T1", 1, TODAY), Channel.REST)).doesNotThrowAnyException();
    }

    @Test
    void auditFailureIsCountedForAlerting() {
        when(repository.save(any())).thenThrow(new IllegalStateException("mongo down"));

        service.accepted(trade("T1", 1, TODAY), Channel.REST);
        service.accepted(trade("T2", 1, TODAY), Channel.KAFKA);

        assertThat(meterRegistry.get("tradestore.audit.failures").tag("outcome", "ACCEPTED").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    void returnsHistoryInOrder() {
        List<TradeAuditEvent> events = List.of();
        when(repository.findByTradeIdOrderByReceivedAtAsc("T1")).thenReturn(events);

        assertThat(service.history("T1")).isSameAs(events);
    }

    private TradeAuditEvent captured() {
        ArgumentCaptor<TradeAuditEvent> captor = ArgumentCaptor.forClass(TradeAuditEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
