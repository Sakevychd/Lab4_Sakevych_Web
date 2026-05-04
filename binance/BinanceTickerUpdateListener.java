package com.example.ssl.binance;

public interface BinanceTickerUpdateListener {

    void onUpdate(BinanceTickerMessage message);
}