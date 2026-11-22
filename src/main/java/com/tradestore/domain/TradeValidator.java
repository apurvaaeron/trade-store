package com.tradestore.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.OptionalInt;
import org.springframework.stereotype.Component;

/** Business rules of the store. Maturity is checked before version so a stale trade is never stored. */
@Component
public class TradeValidator {

    private final Clock clock;

    public TradeValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(TradeRequest trade, OptionalInt currentVersion) {
        LocalDate today = LocalDate.now(clock);
        if (trade.maturityDate().isBefore(today)) {
            throw new MaturityDateInPastException(trade.tradeId(), trade.maturityDate(), today);
        }
        if (currentVersion.isPresent() && trade.version() < currentVersion.getAsInt()) {
            throw new LowerVersionException(trade.tradeId(), trade.version(), currentVersion.getAsInt());
        }
    }
}
