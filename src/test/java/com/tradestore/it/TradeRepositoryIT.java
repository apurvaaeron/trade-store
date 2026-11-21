package com.tradestore.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradestore.domain.Trade;
import com.tradestore.domain.TradeRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class TradeRepositoryIT {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    @Autowired
    private TradeRepository repository;
    @Autowired
    private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void upsertInsertsThenReplacesSameOrHigherVersion() {
        assertThat(upsert(trade("T1", 1, "CP-1"))).isEqualTo(1);
        assertThat(upsert(trade("T1", 1, "CP-2"))).isEqualTo(1);
        assertThat(repository.findById("T1")).get().extracting(Trade::getCounterPartyId).isEqualTo("CP-2");

        assertThat(upsert(trade("T1", 3, "CP-3"))).isEqualTo(1);
        assertThat(repository.findById("T1")).get().extracting(Trade::getVersion).isEqualTo(3);
    }

    @Test
    void upsertNeverLetsALowerVersionOverwrite() {
        upsert(trade("T2", 2, "CP-2"));

        assertThat(upsert(trade("T2", 1, "CP-1"))).isZero();
        assertThat(repository.findById("T2")).get().satisfies(t -> {
            assertThat(t.getVersion()).isEqualTo(2);
            assertThat(t.getCounterPartyId()).isEqualTo("CP-2");
        });
    }

    @Test
    void markExpiredFlagsOnlyActiveTradesMaturedBeforeToday() {
        repository.save(new Trade("OLD", 1, "CP", "B", TODAY.minusDays(1), TODAY, false));
        repository.save(new Trade("TODAY", 1, "CP", "B", TODAY, TODAY, false));
        repository.save(new Trade("ALREADY", 1, "CP", "B", TODAY.minusDays(9), TODAY, true));

        assertThat(expire()).isEqualTo(1);
        assertThat(repository.findById("OLD")).get().extracting(Trade::isExpired).isEqualTo(true);
        assertThat(repository.findById("TODAY")).get().extracting(Trade::isExpired).isEqualTo(false);
        assertThat(expire()).isZero();
    }

    private int expire() {
        Integer expired = tx.execute(s -> repository.markExpired(TODAY));
        return expired;
    }

    private int upsert(Trade trade) {
        Integer rows = tx.execute(s -> repository.upsertUnlessLowerVersion(trade));
        return rows;
    }

    private static Trade trade(String id, int version, String counterParty) {
        return new Trade(id, version, counterParty, "B1", TODAY.plusDays(30), TODAY, false);
    }
}
