package org.example;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class ws_public_demo {
    public static void main(String[] args) {
        String topic = "orderbook.50.BTCUSDT";
        try {
            connectWS(topic);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void connectWS(String topic) throws URISyntaxException, InterruptedException {
        URI uri = new URI("wss://stream-testnet.bybit.com/v5/public/linear");
        CountDownLatch connected = new CountDownLatch(1);

        WebSocketClient ws = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("opened");
                ws_public_demo.onOpen(this, topic);
                connected.countDown();
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
        connected.await();
        while (ws.getReadyState() == ReadyState.OPEN) {
            Thread.sleep(1000);
        }
    }

    static void onOpen(WebSocketClient ws, String topic) {
        Map<String, Object> subscribeRequest = new HashMap<>();
        subscribeRequest.put("op", "subscribe");
        subscribeRequest.put("args", new String[]{topic});

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String subscribeMessage = objectMapper.writeValueAsString(subscribeRequest);
            ws.send(subscribeMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void receive(String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode data = objectMapper.readTree(message);
            System.out.println(data.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
