package com.example.ssl.binance;

public class BinanceTickerMessage {

    private final String symbol;
    private final String price;
    private final long eventTime;

    public BinanceTickerMessage(String symbol, String price, long eventTime) {
        this.symbol = symbol;
        this.price = price;
        this.eventTime = eventTime;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getPrice() {
        return price;
    }

    public long getEventTime() {
        return eventTime;
    }
}