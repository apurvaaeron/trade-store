package com.tradestore.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradestore.audit.Channel;
import com.tradestore.domain.TradeRejectedException;
import com.tradestore.domain.TradeRepository;
import com.tradestore.domain.TradeRequest;
import com.tradestore.service.TradeIngestionService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Many versions of one trade arrive at once, in random order: the highest version must win. */
@IntegrationTest
class ConcurrentVersionsIT {

    @Autowired
    private TradeIngestionService ingestion;
    @Autowired
    private TradeRepository repository;

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
}
