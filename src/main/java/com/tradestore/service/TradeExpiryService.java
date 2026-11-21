package com.tradestore.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.tradestore.domain.TradeRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeExpiryService {

    private static final Logger log = LoggerFactory.getLogger(TradeExpiryService.class);

    private final TradeRepository repository;
    private final Clock clock;
    private final Counter expiredCounter;

    public TradeExpiryService(TradeRepository repository, Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.clock = clock;
        this.expiredCounter = meterRegistry.counter("tradestore.trades.expired");
    }

    /** Marks every stored trade whose maturity date has passed as expired. Idempotent. */
    @Transactional
    public int expireMaturedTrades() {
        LocalDate today = LocalDate.now(clock);
        int expired = repository.markExpired(today);
        expiredCounter.increment(expired);
        log.info("Expiry run for business date {}: {} trade(s) marked expired", today, expired);
        return expired;
    }
}
