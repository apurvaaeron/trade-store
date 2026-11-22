package com.tradestore.domain;

/** Base type for every business rejection; carries a machine-readable reason. */
public class TradeRejectedException extends RuntimeException {

    private final String tradeId;
    private final RejectionReason reason;

    public TradeRejectedException(String tradeId, RejectionReason reason, String message) {
        super(message);
        this.tradeId = tradeId;
        this.reason = reason;
    }

    public String getTradeId() {
        return tradeId;
    }

    public RejectionReason getReason() {
        return reason;
    }
}
