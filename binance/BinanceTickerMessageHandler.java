package com.example.ssl.binance;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BinanceTickerMessageHandler {

    private final List<BinanceTickerUpdateListener> listeners;

    public BinanceTickerMessageHandler(List<BinanceTickerUpdateListener> listeners) {
        this.listeners = listeners;
    }

    public void handleMessage(BinanceTickerMessage message) {
        listeners.forEach(listener -> listener.onUpdate(message));
    }
}