package com.tradestore.domain;

public class InvalidTradeException extends TradeRejectedException {

    public InvalidTradeException(String tradeId, String violations) {
        super(tradeId, RejectionReason.INVALID_PAYLOAD, "Trade %s rejected: %s".formatted(tradeId, violations));
    }
}
