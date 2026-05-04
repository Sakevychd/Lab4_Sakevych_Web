package com.example.ssl.websocket;

import com.example.ssl.binance.BinanceTickerMessage;
import com.example.ssl.binance.BinanceTickerUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.CodedOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserWebSocketHandler extends BinaryWebSocketHandler implements BinanceTickerUpdateListener {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        subscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());

        String userId = (String) session.getAttributes().get("userId");

        System.out.println("WebSocket session created. SessionId: "
                + session.getId() + ", userId: " + userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());

            String type = json.has("type") ? json.get("type").asText() : "";

            if (!"subscribe".equals(type)) {
                return;
            }

            Set<String> userSubscriptions = subscriptions.get(session.getId());

            if (userSubscriptions == null) {
                userSubscriptions = ConcurrentHashMap.newKeySet();
                subscriptions.put(session.getId(), userSubscriptions);
            }

            if (json.has("symbols") && json.get("symbols").isArray()) {
                for (JsonNode symbolNode : json.get("symbols")) {
                    String symbol = symbolNode.asText().toUpperCase();
                    userSubscriptions.add(symbol);
                }
            }

            session.sendMessage(new TextMessage("Subscribed to: " + userSubscriptions));

        } catch (Exception e) {
            System.out.println("Cannot handle text WebSocket message: " + e.getMessage());

            try {
                session.sendMessage(new TextMessage("Invalid subscription message"));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscriptions.remove(session.getId());

        System.out.println("WebSocket session closed. SessionId: " + session.getId());
    }

    @Override
    public void onUpdate(BinanceTickerMessage message) {
        String symbol = message.getSymbol();

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();

            Set<String> userSubscriptions =
                    subscriptions.getOrDefault(sessionId, new HashSet<>());

            if (!userSubscriptions.contains(symbol)) {
                continue;
            }

            if (!session.isOpen()) {
                continue;
            }

            try {
                byte[] protobufBytes = createTickerUpdateProtobuf(
                        message.getSymbol(),
                        message.getPrice(),
                        message.getEventTime()
                );

                session.sendMessage(new BinaryMessage(protobufBytes));

            } catch (Exception e) {
                System.out.println("Cannot send Protobuf message to session "
                        + sessionId + ": " + e.getMessage());
            }
        }
    }

    private byte[] createTickerUpdateProtobuf(String symbol, String price, long eventTime) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);

        codedOutputStream.writeString(1, symbol);
        codedOutputStream.writeString(2, price);
        codedOutputStream.writeInt64(3, eventTime);

        codedOutputStream.flush();

        return outputStream.toByteArray();
    }
}
