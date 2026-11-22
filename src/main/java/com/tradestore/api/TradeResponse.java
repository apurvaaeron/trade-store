package com.tradestore.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradestore.domain.Trade;
import java.time.LocalDate;

public record TradeResponse(
        String tradeId,
        int version,
        String counterPartyId,
        String bookId,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate maturityDate,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate createdDate,
        String expired) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(trade.getTradeId(), trade.getVersion(), trade.getCounterPartyId(),
                trade.getBookId(), trade.getMaturityDate(), trade.getCreatedDate(), trade.isExpired() ? "Y" : "N");
    }
}
