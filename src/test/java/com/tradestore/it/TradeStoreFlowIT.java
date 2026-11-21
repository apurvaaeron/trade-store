package com.tradestore.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tradestore.audit.TradeAuditEvent.Outcome;
import com.tradestore.audit.TradeAuditRepository;
import com.tradestore.domain.TradeRepository;
import com.tradestore.domain.TradeRequest;
import com.tradestore.messaging.TradePublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** End-to-end through real infrastructure: REST and Kafka in, PostgreSQL + MongoDB + rejected topic out. */
@IntegrationTest
class TradeStoreFlowIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private TradePublisher publisher;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private TradeAuditRepository auditRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private KafkaContainer kafkaContainer;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void clean() {
        clock.reset();
        tradeRepository.deleteAll();
        auditRepository.deleteAll();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void restStoresValidTradeAndAuditsIt() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/trades", body("R1", 1, today.plusDays(5)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("expired").asText()).isEqualTo("N");
        assertThat(tradeRepository.findById("R1")).isPresent();
        assertThat(auditRepository.findByTradeIdOrderByReceivedAtAsc("R1"))
                .extracting(e -> e.outcome()).containsExactly(Outcome.ACCEPTED);
    }

    @Test
    void restRejectsLowerVersionAndPastMaturityWith422() {
        rest.postForEntity("/api/v1/trades", body("R2", 2, today.plusDays(5)), JsonNode.class);

        ResponseEntity<JsonNode> lower = rest.postForEntity("/api/v1/trades", body("R2", 1, today.plusDays(5)), JsonNode.class);
        ResponseEntity<JsonNode> past = rest.postForEntity("/api/v1/trades", body("R3", 1, today.minusDays(1)), JsonNode.class);

        assertThat(lower.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(lower.getBody().get("reason").asText()).isEqualTo("LOWER_VERSION");
        assertThat(past.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(past.getBody().get("reason").asText()).isEqualTo("MATURITY_DATE_IN_PAST");
        assertThat(tradeRepository.findById("R3")).isEmpty();
        assertThat(rest.getForEntity("/api/v1/trades/R2/audit", JsonNode.class).getBody()).hasSize(2);
    }

    @Test
    void restRejectsIncompletePayloadWith400() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/trades",
                Map.of("tradeId", "R4", "bookId", "B1"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("reason").asText()).isEqualTo("INVALID_PAYLOAD");
    }

    @Test
    void restRejectsUnsafeIdentifiersWith400() {
        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("tradeId", "R\n4", "version", 1, "counterPartyId", "CP-1", "bookId", "B1",
                        "maturityDate", "20/05/2030"),
                Map.<String, Object>of("tradeId", "R5", "version", 1, "counterPartyId", "CP/1", "bookId", "B1",
                        "maturityDate", "20/05/2030"),
                Map.<String, Object>of("tradeId", "R6", "version", 1, "counterPartyId", "CP-1", "bookId", "B".repeat(65),
                        "maturityDate", "20/05/2030"))) {
            ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/trades", body, JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("reason").asText()).isEqualTo("INVALID_PAYLOAD");
        }
        assertThat(tradeRepository.findById("R5")).isEmpty();
    }

    @Test
    void kafkaStoresTradesAndPublishesRejections() throws Exception {
        try (Consumer<String, String> rejected = rejectedTopicConsumer()) {
            publisher.publish(request("K1", 2, today.plusDays(5))).get();
            await().atMost(Duration.ofSeconds(20)).until(() -> tradeRepository.findById("K1").isPresent());

            publisher.publish(request("K1", 1, today.plusDays(5))).get();
            publisher.publish(request("K2", 1, today.minusDays(3))).get();

            List<JsonNode> rejections = pollRejections(rejected, 2);
            assertThat(rejections).extracting(n -> n.get("tradeId").asText() + ":" + n.get("reason").asText())
                    .containsExactlyInAnyOrder("K1:LOWER_VERSION", "K2:MATURITY_DATE_IN_PAST");
            assertThat(tradeRepository.findById("K1")).get().extracting(t -> t.getVersion()).isEqualTo(2);
            assertThat(tradeRepository.findById("K2")).isEmpty();
        }
    }

    @Test
    void asyncRestEndpointRoutesThroughKafka() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/trades/async", body("A1", 1, today.plusDays(1)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(20)).until(() -> tradeRepository.findById("A1").isPresent());
    }

    @Test
    void poisonMessageGoesToDeadLetterTopicWithoutBlockingThePartition() throws Exception {
        byte[] original = "not-json".getBytes(StandardCharsets.UTF_8);
        try (Consumer<String, byte[]> deadLetter = deadLetterConsumer()) {
            kafkaTemplate.send("trades.inbound", "P1", original).get();
            publisher.publish(request("P2", 1, today.plusDays(1))).get();

            await().atMost(Duration.ofSeconds(30)).until(() -> tradeRepository.findById("P2").isPresent());
            assertThat(pollDeadLetter(deadLetter)).containsExactly(original);
        }
    }

    @Test
    void fractionalKafkaVersionGoesToDeadLetterWithoutAudit() throws Exception {
        byte[] original = """
                {"tradeId":"V1","version":2.9,"counterPartyId":"CP-1","bookId":"B1","maturityDate":"20/05/2030"}
                """.getBytes(StandardCharsets.UTF_8);
        try (Consumer<String, byte[]> deadLetter = deadLetterConsumer()) {
            kafkaTemplate.send("trades.inbound", "V1", original).get();
            assertThat(pollDeadLetter(deadLetter)).containsExactly(original);
        }
        assertThat(tradeRepository.findById("V1")).isEmpty();
        assertThat(auditRepository.findByTradeIdOrderByReceivedAtAsc("V1")).isEmpty();
    }

    @Test
    void invalidCalendarDatesGoToDeadLetterWithoutAudit() throws Exception {
        byte[] invalidMaturity = """
                {"tradeId":"DM1","version":1,"counterPartyId":"CP-1","bookId":"B1","maturityDate":"31/02/2030"}
                """.getBytes(StandardCharsets.UTF_8);
        byte[] invalidCreated = """
                {"tradeId":"DM2","version":1,"counterPartyId":"CP-1","bookId":"B1","maturityDate":"20/05/2030","createdDate":"31/02/2030"}
                """.getBytes(StandardCharsets.UTF_8);
        try (Consumer<String, byte[]> deadLetter = deadLetterConsumer()) {
            kafkaTemplate.send("trades.inbound", "DM1", invalidMaturity).get();
            kafkaTemplate.send("trades.inbound", "DM2", invalidCreated).get();

            assertThat(pollDeadLetters(deadLetter, 2))
                    .extracting(bytes -> new String(bytes, StandardCharsets.UTF_8))
                    .containsExactlyInAnyOrder(new String(invalidMaturity, StandardCharsets.UTF_8),
                            new String(invalidCreated, StandardCharsets.UTF_8));
        }
        assertThat(tradeRepository.findById("DM1")).isEmpty();
        assertThat(tradeRepository.findById("DM2")).isEmpty();
        assertThat(auditRepository.findByTradeIdOrderByReceivedAtAsc("DM1")).isEmpty();
        assertThat(auditRepository.findByTradeIdOrderByReceivedAtAsc("DM2")).isEmpty();
    }

    @Test
    void restRejectsInvalidCalendarDatesWithoutAudit() {
        Map<String, Object> invalidMaturity = Map.of("tradeId", "DR1", "version", 1, "counterPartyId", "CP-1",
                "bookId", "B1", "maturityDate", "31/02/2030");
        Map<String, Object> invalidCreated = Map.of("tradeId", "DR2", "version", 1, "counterPartyId", "CP-1",
                "bookId", "B1", "maturityDate", "20/05/2030", "createdDate", "31/02/2030");

        assertThat(rest.postForEntity("/api/v1/trades", invalidMaturity, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.postForEntity("/api/v1/trades", invalidCreated, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(auditRepository.findByTradeIdOrderByReceivedAtAsc("DR1")).isEmpty();
        assertThat(auditRepository.findByTradeIdOrderByReceivedAtAsc("DR2")).isEmpty();
    }

    @Test
    void expiryRunMarksMaturedTradesAsExpired() {
        rest.postForEntity("/api/v1/trades", body("E1", 1, today.plusDays(1)), JsonNode.class);
        rest.postForEntity("/api/v1/trades", body("E2", 1, today.plusDays(10)), JsonNode.class);

        clock.setToday(today.plusDays(2));
        ResponseEntity<JsonNode> run = rest.postForEntity("/api/v1/trades/expiry-run", null, JsonNode.class);

        assertThat(run.getBody().get("expired").asInt()).isEqualTo(1);
        assertThat(rest.getForObject("/api/v1/trades/E1", JsonNode.class).get("expired").asText()).isEqualTo("Y");
        assertThat(rest.getForObject("/api/v1/trades/E2", JsonNode.class).get("expired").asText()).isEqualTo("N");
    }

    @Test
    void exposesHealthAndPrometheusMetrics() {
        rest.postForEntity("/api/v1/trades", body("M1", 1, today.plusDays(1)), JsonNode.class);

        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForObject("/actuator/prometheus", String.class)).contains("tradestore_trades_received_total");
    }

    private Consumer<String, String> rejectedTopicConsumer() {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                new StringDeserializer(), new StringDeserializer());
        List<TopicPartition> partitions = consumer.partitionsFor("trades.rejected").stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    private Consumer<String, byte[]> deadLetterConsumer() {
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                new StringDeserializer(), new ByteArrayDeserializer());
        List<TopicPartition> partitions = consumer.partitionsFor("trades.inbound.dlt").stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    private List<JsonNode> pollRejections(Consumer<String, String> consumer, int expected) throws Exception {
        List<JsonNode> received = new java.util.ArrayList<>();
        long deadline = System.currentTimeMillis() + 30_000;
        while (received.size() < expected && System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                received.add(objectMapper.readTree(record.value()));
            }
        }
        return received;
    }

    private byte[] pollDeadLetter(Consumer<String, byte[]> consumer) {
        return pollDeadLetters(consumer, 1).get(0);
    }

    private List<byte[]> pollDeadLetters(Consumer<String, byte[]> consumer, int expected) {
        List<byte[]> received = new java.util.ArrayList<>();
        long deadline = System.currentTimeMillis() + 30_000;
        while (received.size() < expected && System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                received.add(record.value());
            }
        }
        if (received.size() == expected) {
            return received;
        }
        throw new AssertionError("No dead-letter record received");
    }

    private static TradeRequest request(String id, int version, LocalDate maturity) {
        return new TradeRequest(id, version, "CP-1", "B1", maturity, null);
    }

    private static Map<String, Object> body(String id, int version, LocalDate maturity) {
        return Map.of("tradeId", id, "version", version, "counterPartyId", "CP-1", "bookId", "B1",
                "maturityDate", "%02d/%02d/%d".formatted(maturity.getDayOfMonth(), maturity.getMonthValue(), maturity.getYear()));
    }
}
