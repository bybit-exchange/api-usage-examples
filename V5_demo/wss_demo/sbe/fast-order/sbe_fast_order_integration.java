package sbe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// -------------------------------------------------------------------
// Config
// -------------------------------------------------------------------
class FastOrderConfig {
    public static final String MMWS_URL_TESTNET = "wss://stream-testnet.bybits.org/v5/private";
    public static final String API_KEY = "xxx";
    public static final String API_SECRET = "xxx";
    public static final String[] SUB_TOPICS = {"order.sbe.resp.linear"};
}

// -------------------------------------------------------------------
// Logger
// -------------------------------------------------------------------
class Logger {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    public void info(String message) {
        String timestamp = dateFormat.format(new Date());
        System.out.println("[" + timestamp + "] INFO: " + message);
    }

    public void error(String message) {
        String timestamp = dateFormat.format(new Date());
        System.err.println("[" + timestamp + "] ERROR: " + message);
    }
}

// -------------------------------------------------------------------
// SBE Parser for Fast Order Response (template_id = 21000)
// -------------------------------------------------------------------
class FastOrderSBEParser {
    private static final int TARGET_TEMPLATE_ID = 21000;

    public Map<String, Object> decodeFastOrderResp(byte[] payload, boolean debug) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, Object> result = new LinkedHashMap<>();

        // Parse header (8 bytes)
        if (payload.length < 8) {
            throw new IllegalArgumentException("payload too short for SBE header");
        }

        short blockLen = buffer.getShort();
        short templateId = buffer.getShort();
        short schemaId = buffer.getShort();
        short version = buffer.getShort();

        if (debug) {
            System.out.printf("HEADER: block_len=%d, template_id=%d, schema_id=%d, version=%d%n",
                    blockLen, templateId, schemaId, version);
            System.out.println("payload hex: " + bytesToHex(payload));
        }

        if (templateId != TARGET_TEMPLATE_ID) {
            return null;
        }

        // Parse fixed fields
        byte category = buffer.get();
        byte side = buffer.get();
        byte orderStatus = buffer.get();
        byte priceExp = buffer.get();
        byte sizeExp = buffer.get();
        byte valueExp = buffer.get();
        short rejectReason = buffer.getShort();
        long price = buffer.getLong();
        long qty = buffer.getLong();
        long leavesQty = buffer.getLong();
        long value = buffer.getLong();
        long leavesValue = buffer.getLong();
        long creationTimeUs = buffer.getLong();
        long updatedTimeUs = buffer.getLong();
        long seq = buffer.getLong();

        if (debug) {
            System.out.println("after fixed fields offset: " + buffer.position());
        }

        // Parse variable length strings
        String symbolName = readVarString8(buffer);
        String orderId = readVarString8(buffer);
        String orderLinkId = readVarString8(buffer);

        // Prepare result
        Map<String, Object> header = new HashMap<>();
        header.put("blockLength", blockLen);
        header.put("templateId", templateId);
        header.put("schemaId", schemaId);
        header.put("version", version);

        result.put("_sbe_header", header);
        result.put("category", category & 0xFF);
        result.put("side", side & 0xFF);
        result.put("orderStatus", orderStatus & 0xFF);
        result.put("priceExponent", priceExp);
        result.put("sizeExponent", sizeExp);
        result.put("valueExponent", valueExp);
        result.put("rejectReason", rejectReason & 0xFFFF);
        result.put("priceMantissa", String.valueOf(price));
        result.put("qtyMantissa", String.valueOf(qty));
        result.put("leavesQtyMantissa", String.valueOf(leavesQty));
        result.put("leavesValueMantissa", String.valueOf(leavesValue));
        result.put("price", applyExp(price, priceExp));
        result.put("qty", applyExp(qty, sizeExp));
        result.put("leavesQty", applyExp(leavesQty, sizeExp));
        result.put("value", applyExp(value, valueExp));
        result.put("leavesValue", applyExp(leavesValue, valueExp));
        result.put("creationTime", String.valueOf(creationTimeUs));
        result.put("updatedTime", String.valueOf(updatedTimeUs));
        result.put("seq", String.valueOf(seq));
        result.put("symbolName", symbolName);
        result.put("orderId", orderId);
        result.put("orderLinkId", orderLinkId);
        result.put("_raw_offset_end", buffer.position());

        return result;
    }

    private String readVarString8(ByteBuffer buffer) {
        int length = buffer.get() & 0xFF;
        if (length == 0) {
            return "";
        }

        if (buffer.remaining() < length) {
            throw new IllegalArgumentException("Insufficient data for varString8 bytes");
        }

        byte[] stringBytes = new byte[length];
        buffer.get(stringBytes);
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    private double applyExp(long mantissa, int exp) {
        if (exp >= 0) {
            return mantissa / Math.pow(10, exp);
        } else {
            return mantissa * Math.pow(10, -exp);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

// -------------------------------------------------------------------
// WebSocket Client with Authentication
// -------------------------------------------------------------------
class FastOrderWebSocketClient extends WebSocketClient {
    private final Logger logger = new Logger();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FastOrderSBEParser parser = new FastOrderSBEParser();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    public FastOrderWebSocketClient() throws Exception {
        super(new URI(FastOrderConfig.MMWS_URL_TESTNET));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("WebSocket connection opened");

        // Authentication
        try {
            authenticate();
        } catch (Exception e) {
            logger.error("Authentication failed: " + e.getMessage());
            close();
        }
    }

    private void authenticate() throws Exception {
        long expires = System.currentTimeMillis() + 10000000; // 10 seconds from now
        String val = "GET/realtime" + expires;
        String signature = generateSignature(val, FastOrderConfig.API_SECRET);

        Map<String, Object> authRequest = new HashMap<>();
        authRequest.put("req_id", "10001");
        authRequest.put("op", "auth");
        authRequest.put("args", Arrays.asList(FastOrderConfig.API_KEY, expires, signature));

        String authJson = objectMapper.writeValueAsString(authRequest);
        send(authJson);
        logger.info("Authentication request sent");
    }

    private String generateSignature(String data, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(signatureBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void sendJson(Object obj) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(obj);
        send(json);
    }

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> obj = objectMapper.readValue(message, Map.class);

            // Don't log pong responses
            if (obj.containsKey("op") && "pong".equals(obj.get("op"))) {
                return;
            }

            logger.info(objectMapper.writeValueAsString(obj));

        } catch (JsonProcessingException e) {
            logger.error("Non-JSON text frame: " + message);
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);

            Map<String, Object> decoded = parser.decodeFastOrderResp(data, false);
            if (decoded != null) {
                String jsonOutput = objectMapper.writeValueAsString(decoded);
                logger.info(jsonOutput);
            }

        } catch (Exception e) {
            logger.error("Binary decode error: " + e.getMessage());
        }
    }

    public void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> ping = new HashMap<>();
                ping.put("req_id", String.valueOf(System.currentTimeMillis()));
                ping.put("op", "ping");

                sendJson(ping);

            } catch (Exception e) {
                logger.error("Failed to send heartbeat: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public void subscribeToTopics() {
        try {
            Map<String, Object> subscribeRequest = new HashMap<>();
            subscribeRequest.put("op", "subscribe");
            subscribeRequest.put("args", FastOrderConfig.SUB_TOPICS);

            sendJson(subscribeRequest);
            logger.info("Subscription request sent");

        } catch (Exception e) {
            logger.error("Failed to subscribe: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Connection closed: code=" + code + ", reason=" + reason);
        heartbeatScheduler.shutdown();
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error: " + ex.getMessage());
    }
}

// -------------------------------------------------------------------
// Main Application
// -------------------------------------------------------------------
public class sbe_fast_order_integration {
    private static final Logger logger = new Logger();

    public static void main(String[] args) {
        try {
            FastOrderWebSocketClient client = new FastOrderWebSocketClient();

            client.connect();

            // Wait for connection to be established
            Thread.sleep(2000);

            // Start heartbeat after connection is established
            client.startHeartbeat();

            // Subscribe to topics
            client.subscribeToTopics();

            // Keep the application running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                client.close();
            }));

            while (true) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            logger.error("Application error: " + e.getMessage());
            System.exit(1);
        }
    }
}
