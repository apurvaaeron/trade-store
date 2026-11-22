package com.tradestore.service;

import static com.tradestore.TestTrades.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradestore.config.TradeStoreProperties;
import com.tradestore.domain.TradeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TradeExpiryServiceTest {

    private final TradeRepository repository = mock(TradeRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TradeExpiryService service = new TradeExpiryService(repository,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), meterRegistry);

    @Test
    void marksTradesMaturedBeforeTodayAsExpiredAndCountsThem() {
        when(repository.markExpired(TODAY)).thenReturn(3);

        assertThat(service.expireMaturedTrades()).isEqualTo(3);
        assertThat(meterRegistry.get("tradestore.trades.expired").counter().count()).isEqualTo(3.0);
    }

    @Test
    void schedulerRunsDailyAndOnStartupWhenEnabled() {
        TradeExpiryService expiry = mock(TradeExpiryService.class);
        TradeExpiryScheduler scheduler = new TradeExpiryScheduler(expiry, properties(true));

        scheduler.daily();
        scheduler.onStartup();

        verify(expiry, org.mockito.Mockito.times(2)).expireMaturedTrades();
    }

    @Test
    void schedulerSkipsStartupRunWhenDisabled() {
        TradeExpiryService expiry = mock(TradeExpiryService.class);

        new TradeExpiryScheduler(expiry, properties(false)).onStartup();

        verify(expiry, never()).expireMaturedTrades();
    }

    private static TradeStoreProperties properties(boolean runOnStartup) {
        return new TradeStoreProperties(ZoneOffset.UTC,
                new TradeStoreProperties.Topics("in", "rej", "dlt", 1),
                new TradeStoreProperties.Expiry("0 0 0 * * *", runOnStartup),
                new TradeStoreProperties.Async(Duration.ofSeconds(5)));
    }
}
