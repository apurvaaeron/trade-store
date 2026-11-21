package com.tradestore.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Inbound trade as received on any channel (REST or Kafka). Dates use the dd/MM/yyyy format of the brief.
 * {@code createdDate} is optional; the store stamps today's business date when it is absent.
 */
public record TradeRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String tradeId,
        @NotNull @Positive @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer version,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String counterPartyId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String bookId,
        @NotNull @JsonFormat(pattern = "dd/MM/yyyy") LocalDate maturityDate,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate createdDate) {
}
