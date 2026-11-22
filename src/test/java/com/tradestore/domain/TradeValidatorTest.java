package com.tradestore.domain;

import static com.tradestore.TestTrades.TODAY;
import static com.tradestore.TestTrades.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TradeValidatorTest {

    private final TradeValidator validator =
            new TradeValidator(Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Nested
    class MaturityDate {

        @Test
        void rejectsMaturityBeforeToday() {
            assertThatThrownBy(() -> validator.validate(trade("T1", 1, TODAY.minusDays(1)), OptionalInt.empty()))
                    .isInstanceOf(MaturityDateInPastException.class)
                    .hasMessageContaining("T1")
                    .extracting(e -> ((TradeRejectedException) e).getReason())
                    .isEqualTo(RejectionReason.MATURITY_DATE_IN_PAST);
        }

        @ParameterizedTest(name = "maturity today+{0} is accepted")
        @CsvSource({"0", "1", "365"})
        void acceptsMaturityTodayOrLater(int daysAhead) {
            assertThatCode(() -> validator.validate(trade("T1", 1, TODAY.plusDays(daysAhead)), OptionalInt.empty()))
                    .doesNotThrowAnyException();
        }

        @Test
        void maturityIsCheckedBeforeVersion() {
            assertThatThrownBy(() -> validator.validate(trade("T1", 1, TODAY.minusDays(1)), OptionalInt.of(5)))
                    .isInstanceOf(MaturityDateInPastException.class);
        }
    }

    @Nested
    class Version {

        @Test
        void rejectsLowerVersion() {
            assertThatThrownBy(() -> validator.validate(trade("T2", 1, TODAY), OptionalInt.of(2)))
                    .isInstanceOf(LowerVersionException.class)
                    .hasMessage("Trade T2 version 1 rejected: current version is 2")
                    .satisfies(e -> assertThat(((TradeRejectedException) e).getTradeId()).isEqualTo("T2"));
        }

        @ParameterizedTest(name = "incoming v{0} vs stored v{1} is accepted")
        @CsvSource({"2, 2", "3, 2", "1, 1"})
        void acceptsSameOrHigherVersion(int incoming, int stored) {
            assertThatCode(() -> validator.validate(trade("T2", incoming, TODAY), OptionalInt.of(stored)))
                    .doesNotThrowAnyException();
        }

        @Test
        void acceptsNewTrade() {
            assertThatCode(() -> validator.validate(trade("T9", 1, TODAY), OptionalInt.empty()))
                    .doesNotThrowAnyException();
        }
    }
}
