package com.bybit.api.client.websocket;

import com.alibaba.fastjson.JSON;
import org.apache.commons.codec.binary.Hex;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ws_private_demo {
    public static void main(String[] args) {
        try {
            connect();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void connect() throws URISyntaxException {
        URI uri = new URI("wss://stream-testnet.bybit.com/v5/private");
        WebSocketClient ws = getWebSocketClient(uri);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            try {
                if (ws.getReadyState() == ReadyState.OPEN) {
                    System.out.println("Ping sent");
                    ws.send("{\"op\":\"ping\"}");
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }, 0, 20, TimeUnit.SECONDS);
    }

    @NotNull
    private static WebSocketClient getWebSocketClient(URI uri) {
        WebSocketClient ws = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Connected.");
                ws_private_demo.onOpen(this);
            }

            @Override
            public void onMessage(String message) {
                System.out.println("Message received : " + message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("### about to close ###");
            }

            @Override
            public void onError(Exception e) {
                System.out.println(e.getMessage());
            }
        };

        ws.connect();
        return ws;
    }

    static void onOpen(WebSocketClient ws) {
        System.out.println("opened");
        sendAuth(ws);
        sendSubscription(ws);
    }

    static void sendAuth(WebSocketClient ws) {
        String key = "xxxxxxxx";
        String secret = "xxxxxxxxxxxxxxx";
        long expires = Instant.now().toEpochMilli() + 10000;
        String _val = "GET/realtime" + expires;

        byte[] hmacSha256;
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            hmacSha256 = mac.doFinal(_val.getBytes());
            var args = List.of(key, expires, Hex.encodeHexString(hmacSha256));
            var authMap = Map.of("req_id", UUID.randomUUID().toString(), "op", "auth", "args", args);
            var authJson =  JSON.toJSONString(authMap);
            System.out.println("Auth Message" + authJson);
            ws.send(authJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hmac-sha256", e);
        }
    }

    static void sendSubscription(WebSocketClient ws) {
        String topic = "order";
        Map<String, Object> subMessage = new HashMap<>();
        subMessage.put("req_id", UUID.randomUUID().toString());
        subMessage.put("op", "subscribe");
        subMessage.put("args", new String[]{topic});
        System.out.println("Subscribe Message" +JSON.toJSONString(subMessage));
        ws.send(JSON.toJSONString(subMessage));
    }
}
