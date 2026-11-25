package com.tradestore.domain;

import static com.tradestore.TestTrades.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TradeTest {

    private final Trade trade = new Trade("T1", 1, "CP-1", "B1", TODAY, TODAY, false);

    @Test
    void equalTradesHaveEqualHashCodes() {
        Trade same = new Trade("T1", 1, "CP-1", "B1", TODAY, TODAY, false);
        assertThat(trade).isEqualTo(same).hasSameHashCodeAs(same);
    }

    @Test
    void differsWhenAnyFieldDiffers() {
        assertThat(trade)
                .isNotEqualTo(new Trade("T2", 1, "CP-1", "B1", TODAY, TODAY, false))
                .isNotEqualTo(new Trade("T1", 2, "CP-1", "B1", TODAY, TODAY, false))
                .isNotEqualTo(new Trade("T1", 1, "CP-2", "B1", TODAY, TODAY, false))
                .isNotEqualTo(new Trade("T1", 1, "CP-1", "B2", TODAY, TODAY, false))
                .isNotEqualTo(new Trade("T1", 1, "CP-1", "B1", TODAY.plusDays(1), TODAY, false))
                .isNotEqualTo(new Trade("T1", 1, "CP-1", "B1", TODAY, TODAY.plusDays(1), false))
                .isNotEqualTo(new Trade("T1", 1, "CP-1", "B1", TODAY, TODAY, true))
                .isNotEqualTo(new Object());
    }

    @Test
    void toStringShowsKeyFields() {
        assertThat(trade.toString()).contains("T1", "version=1", "expired=false");
    }
}
