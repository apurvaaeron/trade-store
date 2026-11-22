package com.tradestore.domain;

public class TradeNotFoundException extends RuntimeException {

    public TradeNotFoundException(String tradeId) {
        super("Trade %s not found".formatted(tradeId));
    }
}
