package com.tradestore.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import static com.tradestore.domain.TradeFormats.DATE_PATTERN;
import static com.tradestore.domain.TradeFormats.ID_PATTERN;

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
        @NotBlank @Pattern(regexp = ID_PATTERN) String tradeId,
        @NotNull @Positive @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer version,
        @NotBlank @Pattern(regexp = ID_PATTERN) String counterPartyId,
        @NotBlank @Pattern(regexp = ID_PATTERN) String bookId,
        @NotNull @JsonFormat(pattern = DATE_PATTERN)
        @JsonDeserialize(using = StrictLocalDateDeserializer.class) LocalDate maturityDate,
        @JsonFormat(pattern = DATE_PATTERN)
        @JsonDeserialize(using = StrictLocalDateDeserializer.class) LocalDate createdDate) {
}
