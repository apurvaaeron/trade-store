package com.tradestore.audit;

import com.tradestore.domain.RejectionReason;
import com.tradestore.domain.TradeRequest;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Immutable record of every trade message received, accepted or rejected. */
@Document("trade_audit")
public record TradeAuditEvent(
        @Id String id,
        @Indexed String tradeId,
        Integer version,
        Channel channel,
        Outcome outcome,
        RejectionReason reason,
        String message,
        TradeRequest payload,
        Instant receivedAt) {

    public enum Outcome {
        ACCEPTED,
        REJECTED
    }
}
