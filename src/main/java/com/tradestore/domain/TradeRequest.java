package com.tradestore.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * Inbound trade as received on any channel (REST or Kafka). Dates use the dd/MM/yyyy format of the brief.
 * {@code createdDate} is optional; the store stamps today's business date when it is absent.
 */
public record TradeRequest(
        @NotBlank String tradeId,
        @NotNull @Positive Integer version,
        @NotBlank String counterPartyId,
        @NotBlank String bookId,
        @NotNull @JsonFormat(pattern = "dd/MM/yyyy") LocalDate maturityDate,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate createdDate) {
}
