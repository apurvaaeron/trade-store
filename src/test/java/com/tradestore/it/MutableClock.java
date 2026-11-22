package com.tradestore.it;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock so integration and acceptance tests control the business date. */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setToday(LocalDate today) {
        this.instant = today.atStartOfDay(zone).toInstant().plusSeconds(3600);
    }

    public void reset() {
        this.instant = Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public static MutableClock utcNow() {
        return new MutableClock(Instant.now(), ZoneOffset.UTC);
    }
}
