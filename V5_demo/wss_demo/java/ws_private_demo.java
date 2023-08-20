package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ws_private_demo {
    public static void main(String[] args) {
        try {
            connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void connect() throws URISyntaxException {
        URI uri = new URI("wss://stream-testnet.bybit.com/v5/private");
        WebSocketClient ws = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("Connected.");
                ws_private_demo.onOpen(this);
            }

            @Override
            public void onMessage(String message) {
                receive(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("### about to close ###");
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        ws.connect();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            try {
                if (ws.getReadyState() == ReadyState.OPEN) {
                    ws.send("ping");
                    System.out.println("ping sent");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 20, TimeUnit.SECONDS);
    }

    static void onOpen(WebSocketClient ws) {
        System.out.println("opened");
        sendAuth(ws);
        sendSubscription(ws);
    }

    static void sendAuth(WebSocketClient ws) {
        String key = "xxxxxxxxx";
        String secret = "xxxxxxxxxxxxxxxxxxxxxxx";
        long expires = Instant.now().toEpochMilli() + 10000;
        String _val = "GET/realtime" + expires;

        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKeySpec);
            byte[] hash = hmacSha256.doFinal(_val.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            String signature = sb.toString();

            Map<String, Object> authMessage = new HashMap<>();
            authMessage.put("op", "auth");
            authMessage.put("args", new Object[]{key, expires, signature});

            ObjectMapper objectMapper = new ObjectMapper();
            String authMessageJson = objectMapper.writeValueAsString(authMessage);
            System.out.println(authMessageJson);
            ws.send(authMessageJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void sendSubscription(WebSocketClient ws) {
        String topic = "order";
        Map<String, Object> subMessage = new HashMap<>();
        subMessage.put("op", "subscribe");
        subMessage.put("args", new String[]{topic});

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String subMessageJson = objectMapper.writeValueAsString(subMessage);
            System.out.println("send subscription " + topic);
            ws.send(subMessageJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void receive(String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            System.out.println(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
