package com.tradestore.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Single source of "today" (business date in the configured zone); replaceable in tests. */
    @Bean
    public Clock clock(TradeStoreProperties properties) {
        return Clock.system(properties.zone());
    }
}
