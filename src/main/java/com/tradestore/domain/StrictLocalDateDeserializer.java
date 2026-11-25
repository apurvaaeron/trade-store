package com.tradestore.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class StrictLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    // "uuuu" (proleptic year) is required by ResolverStyle.STRICT; on the wire it is the same as TradeFormats.DATE_PATTERN.
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern WIRE_DATE = Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{4}");
    private static final String OUT_OF_RANGE =
            "Expected a date in " + TradeFormats.DATE_PATTERN + " with a year from 0001 through 9999";
    private static final String NOT_A_DATE = "Expected a valid date in " + TradeFormats.DATE_PATTERN;

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
        }
        String value = parser.getString();
        if (!WIRE_DATE.matcher(value).matches()) {
            throw context.weirdStringException(value, LocalDate.class, OUT_OF_RANGE);
        }
        try {
            LocalDate date = LocalDate.parse(value, FORMATTER);
            if (date.getYear() < 1 || date.getYear() > 9999) {
                throw context.weirdStringException(value, LocalDate.class, OUT_OF_RANGE);
            }
            return date;
        } catch (DateTimeParseException e) {
            throw context.weirdStringException(value, LocalDate.class, NOT_A_DATE);
        }
    }
}
