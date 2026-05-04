package com.example.ssl.binance;

import com.fasterxml.jackson.databind.JsonNode;

public class BinanceWebSocketMessage {

    private final String stream;
    private final JsonNode data;

    public BinanceWebSocketMessage(String stream, JsonNode data) {
        this.stream = stream;
        this.data = data;
    }

    public String getStream() {
        return stream;
    }

    public JsonNode getData() {
        return data;
    }
}