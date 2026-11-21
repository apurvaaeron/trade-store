package com.tradestore.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tradestore")
public record TradeStoreProperties(
        @DefaultValue("UTC") ZoneId zone,
        @DefaultValue Topics topics,
        @DefaultValue Expiry expiry) {

    public record Topics(
            @DefaultValue("trades.inbound") String inbound,
            @DefaultValue("trades.rejected") String rejected,
            @DefaultValue("trades.inbound.dlt") String deadLetter,
            @DefaultValue("3") int partitions) {
    }

    public record Expiry(
            @DefaultValue("0 0 0 * * *") String cron,
            @DefaultValue("true") boolean runOnStartup) {
    }
}
