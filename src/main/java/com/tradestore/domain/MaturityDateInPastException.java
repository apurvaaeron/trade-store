package com.tradestore.domain;

import java.time.LocalDate;

public class MaturityDateInPastException extends TradeRejectedException {

    public MaturityDateInPastException(String tradeId, LocalDate maturityDate, LocalDate today) {
        super(tradeId, RejectionReason.MATURITY_DATE_IN_PAST,
                "Trade %s rejected: maturity date %s is before today %s".formatted(tradeId, maturityDate, today));
    }
}
