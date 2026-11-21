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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern WIRE_DATE = Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{4}");

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
        }
        String value = parser.getString();
        if (!WIRE_DATE.matcher(value).matches()) {
            throw context.weirdStringException(value, LocalDate.class,
                    "Expected a date in dd/MM/yyyy with a year from 0001 through 9999");
        }
        try {
            LocalDate date = LocalDate.parse(value, FORMATTER);
            if (date.getYear() < 1 || date.getYear() > 9999) {
                throw context.weirdStringException(value, LocalDate.class,
                        "Expected a date in dd/MM/yyyy with a year from 0001 through 9999");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw context.weirdStringException(value, LocalDate.class, "Expected a valid date in dd/MM/yyyy");
        }
    }
}
