package com.tradestore.service;

import static com.tradestore.TestTrades.TODAY;
import static com.tradestore.TestTrades.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradestore.audit.Channel;
import com.tradestore.audit.TradeAuditService;
import com.tradestore.domain.InvalidTradeException;
import com.tradestore.domain.LowerVersionException;
import com.tradestore.domain.RejectionReason;
import com.tradestore.domain.Trade;
import com.tradestore.domain.TradeRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TradeIngestionServiceTest {

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final TradeStoreService storeService = mock(TradeStoreService.class);
    private final TradeAuditService auditService = mock(TradeAuditService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TradeIngestionService service = new TradeIngestionService(storeService, auditService,
            validatorFactory.getValidator(), meterRegistry);

    @AfterEach
    void close() {
        validatorFactory.close();
    }

    @Test
    void storesAuditsAndCountsAcceptedTrade() {
        TradeRequest request = trade("T1", 1, TODAY);
        Trade stored = new Trade("T1", 1, "CP-1", "B1", TODAY, TODAY, false);
        when(storeService.store(request)).thenReturn(stored);

        assertThat(service.ingest(request, Channel.REST)).isSameAs(stored);

        verify(auditService).accepted(request, Channel.REST);
        assertThat(meterRegistry.get(TradeIngestionService.METRIC)
                .tags("channel", "REST", "outcome", "accepted").counter().count()).isEqualTo(1.0);
    }

    @Test
    void auditsCountsAndRethrowsBusinessRejection() {
        TradeRequest request = trade("T2", 1, TODAY);
        LowerVersionException rejection = new LowerVersionException("T2", 1, 2);
        when(storeService.store(request)).thenThrow(rejection);

        assertThatThrownBy(() -> service.ingest(request, Channel.KAFKA)).isSameAs(rejection);

        verify(auditService).rejected(request, Channel.KAFKA, rejection);
        verify(auditService, never()).accepted(any(), any());
        assertThat(meterRegistry.get(TradeIngestionService.METRIC)
                .tags("channel", "KAFKA", "outcome", "rejected", "reason", "LOWER_VERSION").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void rejectsIncompletePayloadBeforeTouchingTheStore() {
        TradeRequest request = new TradeRequest("T5", null, "", "B1", null, null);

        assertThatThrownBy(() -> service.ingest(request, Channel.REST))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("counterPartyId")
                .hasMessageContaining("maturityDate")
                .hasMessageContaining("version")
                .extracting(e -> ((InvalidTradeException) e).getReason())
                .isEqualTo(RejectionReason.INVALID_PAYLOAD);

        verify(storeService, never()).store(any());
        verify(auditService).rejected(eq(request), eq(Channel.REST), any(InvalidTradeException.class));
    }
}
