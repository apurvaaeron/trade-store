package com.tradestore.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, String> {

    /**
     * Atomic insert-or-replace that never lets a lower version overwrite a higher one, even when
     * two consumers race on the same trade. Returns 0 when the stored version is higher.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO trades (trade_id, version, counter_party_id, book_id, maturity_date, created_date, expired)
            VALUES (:#{#t.tradeId}, :#{#t.version}, :#{#t.counterPartyId}, :#{#t.bookId},
                    :#{#t.maturityDate}, :#{#t.createdDate}, :#{#t.expired})
            ON CONFLICT (trade_id) DO UPDATE SET
                version = EXCLUDED.version,
                counter_party_id = EXCLUDED.counter_party_id,
                book_id = EXCLUDED.book_id,
                maturity_date = EXCLUDED.maturity_date,
                created_date = EXCLUDED.created_date,
                expired = EXCLUDED.expired
            WHERE trades.version <= EXCLUDED.version
            """, nativeQuery = true)
    int upsertUnlessLowerVersion(@Param("t") Trade trade);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Trade t SET t.expired = true WHERE t.maturityDate < :today AND t.expired = false")
    int markExpired(@Param("today") LocalDate today);

    List<Trade> findAllByOrderByTradeIdAsc();
}
