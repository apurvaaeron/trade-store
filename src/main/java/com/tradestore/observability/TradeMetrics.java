package com.tradestore.observability;

/** Metric names and tags published to Prometheus; dashboards and alerts depend on them. */
public final class TradeMetrics {

    public static final String TRADES_RECEIVED = "tradestore.trades.received";
    public static final String TRADES_EXPIRED = "tradestore.trades.expired";
    public static final String AUDIT_FAILURES = "tradestore.audit.failures";

    public static final String TAG_CHANNEL = "channel";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_REASON = "reason";

    public static final String OUTCOME_ACCEPTED = "accepted";
    public static final String OUTCOME_REJECTED = "rejected";
    public static final String REASON_NONE = "none";

    private TradeMetrics() {
    }
}
