package com.tradestore.messaging;

import com.tradestore.domain.RejectionReason;
import com.tradestore.domain.TradeRequest;
import java.time.Instant;

/** Published to the rejected-trades topic so upstream systems can see and correct rejected trades. */
public record TradeRejection(
        String tradeId,
        Integer version,
        RejectionReason reason,
        String message,
        TradeRequest trade,
        Instant rejectedAt) {
}
