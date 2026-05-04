package com.example.ssl.binance;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

@Component
public class BinanceWebSocketTickerStreamClient implements WebSocket.Listener {

    @Value("${binance.websocket.url}")
    private String binanceWebSocketUrl;

    private final BinanceWebSocketMessageSerDe messageSerDe;
    private final BinanceTickerMessageHandler messageHandler;

    private WebSocket webSocket;

    public BinanceWebSocketTickerStreamClient(BinanceWebSocketMessageSerDe messageSerDe,
                                              BinanceTickerMessageHandler messageHandler) {
        this.messageSerDe = messageSerDe;
        this.messageHandler = messageHandler;
    }

    @PostConstruct
    public void connect() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(binanceWebSocketUrl), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    System.out.println("Connected to Binance WebSocket");
                })
                .exceptionally(ex -> {
                    System.out.println("Cannot connect to Binance WebSocket: " + ex.getMessage());
                    return null;
                });
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("Binance WebSocket opened");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket,
                                     CharSequence data,
                                     boolean last) {
        try {
            BinanceTickerMessage tickerMessage =
                    messageSerDe.deserializeTickerMessage(data.toString());

            messageHandler.handleMessage(tickerMessage);

        } catch (Exception e) {
            System.out.println("Cannot handle Binance message: " + e.getMessage());
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket,
                                      int statusCode,
                                      String reason) {
        System.out.println("Binance WebSocket closed: " + statusCode + " " + reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.out.println("Binance WebSocket error: " + error.getMessage());
    }
}