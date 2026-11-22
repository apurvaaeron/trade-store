package com.tradestore.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradestore.domain.TradeRepository;
import com.tradestore.domain.TradeRequest;
import com.tradestore.messaging.TradePublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The rejected topic is unusable, so publishing a business rejection fails. The listener must not commit
 * the record as handled: it is retried and then parked, unchanged, on the dead-letter topic.
 */
@IntegrationTest
@TestPropertySource(properties = "tradestore.topics.rejected=invalid topic name")
class RejectionPublishFailureIT {

    @Autowired
    private TradePublisher publisher;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void failedRejectionPublishIsRetriedThenDeadLettered() throws Exception {
        LocalDate pastMaturity = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        try (KafkaConsumer<String, String> deadLetters = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                new StringDeserializer(), new StringDeserializer())) {
            List<TopicPartition> partitions = deadLetters.partitionsFor("trades.inbound.dlt").stream()
                    .map(p -> new TopicPartition(p.topic(), p.partition()))
                    .toList();
            deadLetters.assign(partitions);
            deadLetters.seekToEnd(partitions);
            partitions.forEach(deadLetters::position);

            publisher.publish(new TradeRequest("RJ1", 1, "CP-1", "B1", pastMaturity, null)).get();

            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 90_000;
            while (received.isEmpty() && System.currentTimeMillis() < deadline) {
                deadLetters.poll(Duration.ofMillis(500)).forEach(received::add);
            }

            assertThat(received).singleElement().satisfies(record -> {
                assertThat(record.key()).isEqualTo("RJ1");
                assertThat(record.value()).contains("\"tradeId\":\"RJ1\"");
                assertThat(new String(record.headers().lastHeader("kafka_dlt-exception-message").value(),
                        StandardCharsets.UTF_8)).contains("TradeKafkaListener.onTrade");
            });
        }
        assertThat(tradeRepository.findById("RJ1")).isEmpty();
    }
}
