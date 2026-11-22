package com.tradestore;

import com.tradestore.domain.TradeRequest;
import java.time.LocalDate;

public final class TestTrades {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    private TestTrades() {
    }

    public static TradeRequest trade(String id, int version, LocalDate maturity) {
        return new TradeRequest(id, version, "CP-1", "B1", maturity, null);
    }

    public static TradeRequest trade(String id, int version, String counterParty, String book,
                                     LocalDate maturity, LocalDate created) {
        return new TradeRequest(id, version, counterParty, book, maturity, created);
    }
}
