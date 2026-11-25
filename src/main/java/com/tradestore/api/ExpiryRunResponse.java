package com.tradestore.api;

/** Number of trades marked expired by a manually triggered expiry run. */
public record ExpiryRunResponse(int expired) {
}
