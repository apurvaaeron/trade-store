package com.tradestore.it;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL, MongoDB and Kafka via Testcontainers, wired in with @ServiceConnection. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return MutableClock.utcNow();
    }

    @Bean
    TradeStoreClient tradeStoreClient(ObjectProvider<RestTestClient> restTestClient) {
        return new TradeStoreClient(restTestClient);
    }
}
