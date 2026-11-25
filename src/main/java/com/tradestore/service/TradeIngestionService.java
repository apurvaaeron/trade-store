package com.tradestore.service;

import static com.tradestore.observability.TradeMetrics.OUTCOME_ACCEPTED;
import static com.tradestore.observability.TradeMetrics.OUTCOME_REJECTED;
import static com.tradestore.observability.TradeMetrics.REASON_NONE;
import static com.tradestore.observability.TradeMetrics.TAG_CHANNEL;
import static com.tradestore.observability.TradeMetrics.TAG_OUTCOME;
import static com.tradestore.observability.TradeMetrics.TAG_REASON;
import static com.tradestore.observability.TradeMetrics.TRADES_RECEIVED;

import com.tradestore.audit.Channel;
import com.tradestore.audit.TradeAuditService;
import com.tradestore.domain.InvalidTradeException;
import com.tradestore.domain.Trade;
import com.tradestore.domain.TradeRejectedException;
import com.tradestore.domain.TradeRequest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Single entry point for every transmission channel: validate, store, audit, measure. */
@Service
public class TradeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TradeIngestionService.class);

    private final TradeStoreService storeService;
    private final TradeAuditService auditService;
    private final Validator validator;
    private final MeterRegistry meterRegistry;

    public TradeIngestionService(TradeStoreService storeService, TradeAuditService auditService,
                                 Validator validator, MeterRegistry meterRegistry) {
        this.storeService = storeService;
        this.auditService = auditService;
        this.validator = validator;
        this.meterRegistry = meterRegistry;
    }

    public Trade ingest(TradeRequest request, Channel channel) {
        try {
            checkPayload(request);
            Trade stored = storeService.store(request);
            auditService.accepted(request, channel);
            meterRegistry.counter(TRADES_RECEIVED, TAG_CHANNEL, channel.name(), TAG_OUTCOME, OUTCOME_ACCEPTED,
                    TAG_REASON, REASON_NONE).increment();
            log.info("Trade {} v{} accepted via {}", stored.getTradeId(), stored.getVersion(), channel);
            return stored;
        } catch (TradeRejectedException e) {
            auditService.rejected(request, channel, e);
            meterRegistry.counter(TRADES_RECEIVED, TAG_CHANNEL, channel.name(), TAG_OUTCOME, OUTCOME_REJECTED,
                    TAG_REASON, e.getReason().name()).increment();
            log.warn("Trade {} rejected via {}: {}", sanitizeForLog(e.getTradeId()), channel,
                    sanitizeForLog(e.getMessage()));
            throw e;
        }
    }

    private static String sanitizeForLog(String value) {
        return value == null ? "null" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private void checkPayload(TradeRequest request) {
        Set<ConstraintViolation<TradeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new InvalidTradeException(request.tradeId(), details);
        }
    }
}
