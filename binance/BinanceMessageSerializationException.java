package com.example.ssl.binance;

public class BinanceMessageSerializationException extends RuntimeException {

    public BinanceMessageSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BinanceMessageSerializationException(String message) {
        super(message);
    }
}
