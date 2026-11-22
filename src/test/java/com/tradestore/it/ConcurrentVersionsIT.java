package com.tradestore.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tradestore.audit.Channel;
import com.tradestore.domain.TradeRejectedException;
import com.tradestore.domain.TradeRepository;
import com.tradestore.domain.TradeRequest;
import com.tradestore.messaging.TradePublisher;
import com.tradestore.service.TradeIngestionService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;

/** Many versions of one trade arrive at once, in random order: the highest version must win. */
@IntegrationTest
class ConcurrentVersionsIT {

    @Autowired
    private TradeIngestionService ingestion;
    @Autowired
    private TradeRepository repository;
    @Autowired
    private TradePublisher publisher;
    @Autowired
    private TestRestTemplate rest;

    @Test
    void highestVersionAlwaysWinsUnderConcurrentDelivery() throws Exception {
        repository.deleteById("C1");
        List<Integer> versions = new ArrayList<>(IntStream.rangeClosed(1, 40).boxed().toList());
        Collections.shuffle(versions);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = versions.stream().<Future<?>>map(v -> pool.submit(() -> {
                start.await();
                try {
                    ingestion.ingest(new TradeRequest("C1", v, "CP-" + v, "B1", LocalDate.now(ZoneOffset.UTC).plusDays(1), null),
                            Channel.KAFKA);
                } catch (TradeRejectedException expected) {
                    // a lower version arriving after a higher one is rejected by design
                }
                return null;
            })).toList();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(repository.findById("C1")).get().satisfies(t -> {
            assertThat(t.getVersion()).isEqualTo(40);
            assertThat(t.getCounterPartyId()).isEqualTo("CP-40");
        });
    }

    /** REST and Kafka race on one trade. No ordering between channels is promised, only the version rule. */
    @Test
    void highestVersionWinsWhenRestAndKafkaRace() throws Exception {
        repository.deleteById("X1");
        LocalDate maturity = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        List<Integer> versions = new ArrayList<>(IntStream.rangeClosed(1, 31).boxed().toList());
        Collections.shuffle(versions);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = versions.stream().<Future<?>>map(v -> pool.submit(() -> {
                start.await();
                if (v % 2 == 0) {
                    rest.postForEntity("/api/v1/trades", Map.of("tradeId", "X1", "version", v,
                            "counterPartyId", "CP-" + v, "bookId", "B1",
                            "maturityDate", "%02d/%02d/%d".formatted(maturity.getDayOfMonth(),
                                    maturity.getMonthValue(), maturity.getYear())), String.class);
                } else {
                    publisher.publish(new TradeRequest("X1", v, "CP-" + v, "B1", maturity, null)).get();
                }
                return null;
            })).toList();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        await().atMost(Duration.ofSeconds(30))
                .until(() -> repository.findById("X1").map(t -> t.getVersion() == 31).orElse(false));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .until(() -> repository.findById("X1").map(t -> t.getVersion() == 31
                        && t.getCounterPartyId().equals("CP-31")).orElse(false));
    }
}
