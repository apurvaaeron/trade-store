package com.tradestore.domain;

/** Wire formats shared by the inbound and outbound trade contracts. */
public final class TradeFormats {

    /** Date format of the brief, used on every channel. */
    public static final String DATE_PATTERN = "dd/MM/yyyy";

    /** Allowed characters and length for trade, counterparty and book identifiers. */
    public static final String ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    private TradeFormats() {
    }
}
