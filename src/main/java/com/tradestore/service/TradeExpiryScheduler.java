package com.tradestore.service;

import com.tradestore.config.TradeStoreProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs expiry daily and once at start-up, so a missed midnight run (deploy, outage) is caught up. */
@Component
public class TradeExpiryScheduler {

    private final TradeExpiryService expiryService;
    private final TradeStoreProperties properties;

    public TradeExpiryScheduler(TradeExpiryService expiryService, TradeStoreProperties properties) {
        this.expiryService = expiryService;
        this.properties = properties;
    }

    @Scheduled(cron = "${tradestore.expiry.cron:0 0 0 * * *}", zone = "${tradestore.zone:UTC}")
    public void daily() {
        expiryService.expireMaturedTrades();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (properties.expiry().runOnStartup()) {
            expiryService.expireMaturedTrades();
        }
    }
}
