package com.tradestore.messaging;

/** The async endpoint could not confirm that Kafka stored the trade. */
public class TradePublishException extends RuntimeException {

    /** FAILED: Kafka reported an error. UNKNOWN: no answer in time, so the trade may still have been stored. */
    public enum Outcome { FAILED, UNKNOWN }

    private final String tradeId;
    private final Outcome outcome;

    public TradePublishException(String tradeId, Outcome outcome, Throwable cause) {
        super(outcome == Outcome.UNKNOWN
                ? "Kafka did not confirm trade " + tradeId + " in time; it may or may not have been queued"
                : "Kafka rejected trade " + tradeId + "; it was not confirmed as queued", cause);
        this.tradeId = tradeId;
        this.outcome = outcome;
    }

    public String getTradeId() {
        return tradeId;
    }

    public Outcome getOutcome() {
        return outcome;
    }
}
