package com.tradestore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @Column(name = "trade_id", nullable = false, updatable = false)
    private String tradeId;

    @Column(nullable = false)
    private int version;

    @Column(name = "counter_party_id", nullable = false)
    private String counterPartyId;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    @Column(nullable = false)
    private boolean expired;

    protected Trade() {
    }

    public Trade(String tradeId, int version, String counterPartyId, String bookId,
                 LocalDate maturityDate, LocalDate createdDate, boolean expired) {
        this.tradeId = tradeId;
        this.version = version;
        this.counterPartyId = counterPartyId;
        this.bookId = bookId;
        this.maturityDate = maturityDate;
        this.createdDate = createdDate;
        this.expired = expired;
    }

    public String getTradeId() {
        return tradeId;
    }

    public int getVersion() {
        return version;
    }

    public String getCounterPartyId() {
        return counterPartyId;
    }

    public String getBookId() {
        return bookId;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public boolean isExpired() {
        return expired;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Trade other)) {
            return false;
        }
        return version == other.version
                && expired == other.expired
                && Objects.equals(tradeId, other.tradeId)
                && Objects.equals(counterPartyId, other.counterPartyId)
                && Objects.equals(bookId, other.bookId)
                && Objects.equals(maturityDate, other.maturityDate)
                && Objects.equals(createdDate, other.createdDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId, version, counterPartyId, bookId, maturityDate, createdDate, expired);
    }

    @Override
    public String toString() {
        return "Trade{tradeId='%s', version=%d, counterPartyId='%s', bookId='%s', maturityDate=%s, createdDate=%s, expired=%s}"
                .formatted(tradeId, version, counterPartyId, bookId, maturityDate, createdDate, expired);
    }
}
