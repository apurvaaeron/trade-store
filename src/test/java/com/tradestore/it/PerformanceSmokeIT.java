package com.tradestore.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tradestore.domain.TradeRepository;
import com.tradestore.domain.TradeRequest;
import com.tradestore.messaging.TradePublisher;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Smoke-level throughput check in CI ("thousands of trades"). Not a benchmark: it guards against
 * regressions that would make the store an order of magnitude slower.
 */
@IntegrationTest
class PerformanceSmokeIT {

    private static final int TRADES = 5_000;

    @Autowired
    private TradePublisher publisher;
    @Autowired
    private TradeRepository repository;

    @Test
    void ingestsThousandsOfTradesFromKafka() {
        LocalDate maturity = LocalDate.now(ZoneOffset.UTC).plusYears(1);
        repository.deleteAllInBatch();
        long start = System.nanoTime();

        CompletableFuture.allOf(IntStream.range(0, TRADES)
                .mapToObj(i -> publisher.publish(new TradeRequest("PERF-" + i, 1, "CP-1", "B1", maturity, null)))
                .toArray(CompletableFuture[]::new)).join();

        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500))
                .until(() -> repository.count() >= TRADES);
        double seconds = (System.nanoTime() - start) / 1e9;
        double throughput = TRADES / seconds;
        System.out.printf("PerformanceSmokeIT: %d trades in %.1fs = %.0f trades/s%n", TRADES, seconds, throughput);

        assertThat(throughput).isGreaterThan(100);
        repository.deleteAllInBatch();
    }
}
