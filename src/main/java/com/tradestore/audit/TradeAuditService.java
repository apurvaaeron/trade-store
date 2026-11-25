package com.tradestore.audit;

import com.tradestore.audit.TradeAuditEvent.Outcome;
import com.tradestore.domain.TradeRejectedException;
import com.tradestore.domain.TradeRequest;
import com.tradestore.observability.TradeMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes the audit trail to MongoDB. PostgreSQL stays the source of truth: an audit failure is logged
 * and counted in {@code tradestore.audit.failures} for alerting, and never undoes or blocks an accepted
 * trade (see ADR-0002 for the outbox follow-up).
 */
@Service
public class TradeAuditService {

    private static final Logger log = LoggerFactory.getLogger(TradeAuditService.class);

    private final TradeAuditRepository repository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public TradeAuditService(TradeAuditRepository repository, Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public void accepted(TradeRequest request, Channel channel) {
        save(new TradeAuditEvent(null, request.tradeId(), request.version(), channel, Outcome.ACCEPTED,
                null, null, request, clock.instant()));
    }

    public void rejected(TradeRequest request, Channel channel, TradeRejectedException rejection) {
        save(new TradeAuditEvent(null, rejection.getTradeId(), request.version(), channel, Outcome.REJECTED,
                rejection.getReason(), rejection.getMessage(), request, clock.instant()));
    }

    public List<TradeAuditEvent> history(String tradeId) {
        return repository.findByTradeIdOrderByReceivedAtAsc(tradeId);
    }

    private void save(TradeAuditEvent event) {
        try {
            repository.save(event);
        } catch (RuntimeException e) {
            log.error("Audit write failed for trade {} ({})", event.tradeId(), event.outcome(), e);
            Counter.builder(TradeMetrics.AUDIT_FAILURES)
                    .description("Audit events that could not be written to MongoDB")
                    .tag(TradeMetrics.TAG_OUTCOME, event.outcome().name())
                    .register(meterRegistry)
                    .increment();
        }
    }
}
