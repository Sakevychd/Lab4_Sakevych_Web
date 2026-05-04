package com.example.ssl.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class BinanceWebSocketMessageSerDe {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BinanceWebSocketMessage deserializeRawMessage(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);

            String stream = root.has("stream") ? root.get("stream").asText() : "";
            JsonNode data = root.has("data") ? root.get("data") : root;

            return new BinanceWebSocketMessage(stream, data);
        } catch (Exception e) {
            throw new BinanceMessageSerializationException("Cannot deserialize Binance message", e);
        }
    }

    public BinanceTickerMessage deserializeTickerMessage(String rawMessage) {
        BinanceWebSocketMessage webSocketMessage = deserializeRawMessage(rawMessage);
        JsonNode data = webSocketMessage.getData();

        if (data == null || data.isNull()) {
            throw new BinanceMessageSerializationException("Binance message does not contain data");
        }

        String symbol = data.has("s") ? data.get("s").asText() : null;
        String price = data.has("c") ? data.get("c").asText() : null;
        long eventTime = data.has("E") ? data.get("E").asLong() : System.currentTimeMillis();

        if (symbol == null || price == null) {
            throw new BinanceMessageSerializationException("Binance ticker message does not contain symbol or price");
        }

        return new BinanceTickerMessage(symbol, price, eventTime);
    }
}