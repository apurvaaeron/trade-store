package com.tradestore.api;

/** Body of the 202 returned once Kafka has confirmed an asynchronously submitted trade. */
public record TradeQueuedResponse(String tradeId, String status) {

    private static final String QUEUED = "QUEUED";

    public static TradeQueuedResponse queued(String tradeId) {
        return new TradeQueuedResponse(tradeId, QUEUED);
    }
}
