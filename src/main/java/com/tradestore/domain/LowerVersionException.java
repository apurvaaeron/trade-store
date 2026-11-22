package com.tradestore.domain;

public class LowerVersionException extends TradeRejectedException {

    public LowerVersionException(String tradeId, int receivedVersion, int currentVersion) {
        super(tradeId, RejectionReason.LOWER_VERSION,
                "Trade %s version %d rejected: current version is %d".formatted(tradeId, receivedVersion, currentVersion));
    }
}
