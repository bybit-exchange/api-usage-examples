package sbe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class sbe_bbo_integration {

    private static final String TOPIC = "ob.rpi.1.sbe.BTCUSDT";
    private static final String WS_URL = "wss://stream-testnet.bybits.org/v5/realtime/spot";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static PrintWriter logWriter;
    private static final SBEBestOBRpiParser parser = new SBEBestOBRpiParser();

    static {
        try {
            logWriter = new PrintWriter(new FileWriter("logfile_wrapper.log", true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        connectWebSocket();
    }

    private static void connectWebSocket() {
        try {
            URI serverUri = new URI(WS_URL);
            WebSocketClient client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    onOpenHandler(this);
                }

                @Override
                public void onMessage(String message) {
                    onMessageHandler(message, false);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    onMessageHandler(bytes, true);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    onCloseHandler();
                }

                @Override
                public void onError(Exception ex) {
                    onErrorHandler(ex);
                }
            };

            client.connect();

            // Keep the program running
            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            logError("Failed to connect: " + e.getMessage());
        }
    }

    private static void onMessageHandler(Object message, boolean isBinary) {
        try {
            if (isBinary && message instanceof ByteBuffer) {
                ByteBuffer buffer = (ByteBuffer) message;
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                Map<String, Object> decoded = parser.parse(data);
                logInfo(String.format(
                        "SBE %s seq=%s u=%s NORM bid=%.8f@%.8f ask=%.8f@%.8f RPI bid=%.8f@%.8f ask=%.8f@%.8f ts=%s",
                        decoded.get("symbol"),
                        decoded.get("seq"),
                        decoded.get("u"),
                        decoded.get("bid_normal_price"),
                        decoded.get("bid_normal_size"),
                        decoded.get("ask_normal_price"),
                        decoded.get("ask_normal_size"),
                        decoded.get("bid_rpi_price"),
                        decoded.get("bid_rpi_size"),
                        decoded.get("ask_rpi_price"),
                        decoded.get("ask_rpi_size"),
                        decoded.get("ts")
                ));

                System.out.printf("%s u=%s NORM: %.8f x %.8f | %.8f x %.8f RPI: %.8f x %.8f | %.8f x %.8f (seq=%s ts=%s)%n",
                        decoded.get("symbol"),
                        decoded.get("u"),
                        decoded.get("bid_normal_price"),
                        decoded.get("bid_normal_size"),
                        decoded.get("ask_normal_price"),
                        decoded.get("ask_normal_size"),
                        decoded.get("bid_rpi_price"),
                        decoded.get("bid_rpi_size"),
                        decoded.get("ask_rpi_price"),
                        decoded.get("ask_rpi_size"),
                        decoded.get("seq"),
                        decoded.get("ts")
                );

            } else if (message instanceof String) {
                try {
                    JsonNode obj = objectMapper.readTree((String) message);
                    logInfo("TEXT " + obj.toString());
                    System.out.println(obj);
                } catch (Exception e) {
                    logWarning("non-JSON text frame: " + message);
                }
            }
        } catch (Exception e) {
            logError("decode error: " + e.getMessage());
            System.out.println("decode error: " + e.getMessage());
        }
    }

    private static void onOpenHandler(WebSocketClient client) {
        System.out.println("opened");
        Map<String, Object> sub = new HashMap<>();
        sub.put("op", "subscribe");
        sub.put("args", new String[]{TOPIC});

        try {
            client.send(objectMapper.writeValueAsString(sub));
            System.out.println("subscribed: " + TOPIC);

            // Start ping and subscription management
            startPingTask(client);
            startSubscriptionManagement(client);

        } catch (Exception e) {
            logError("Failed to subscribe: " + e.getMessage());
        }
    }

    private static void startPingTask(WebSocketClient client) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (client.isOpen()) {
                    Map<String, String> ping = new HashMap<>();
                    ping.put("op", "ping");
                    client.send(objectMapper.writeValueAsString(ping));
                }
            } catch (Exception e) {
                System.out.println("Ping error: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private static void startSubscriptionManagement(WebSocketClient client) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Unsubscribe after 20 seconds
        scheduler.schedule(() -> {
            try {
                if (client.isOpen()) {
                    Map<String, Object> unsubscribe = new HashMap<>();
                    unsubscribe.put("op", "unsubscribe");
                    unsubscribe.put("args", new String[]{TOPIC});
                    client.send(objectMapper.writeValueAsString(unsubscribe));
                    System.out.println("unsubscribed: " + TOPIC);
                }
            } catch (Exception e) {
                logError("Failed to unsubscribe: " + e.getMessage());
            }
        }, 20, TimeUnit.SECONDS);

        // Resubscribe after 25 seconds
        scheduler.schedule(() -> {
            try {
                if (client.isOpen()) {
                    Map<String, Object> subscribe = new HashMap<>();
                    subscribe.put("op", "subscribe");
                    subscribe.put("args", new String[]{TOPIC});
                    client.send(objectMapper.writeValueAsString(subscribe));
                    System.out.println("resubscribed: " + TOPIC);
                }
            } catch (Exception e) {
                logError("Failed to resubscribe: " + e.getMessage());
            }
        }, 25, TimeUnit.SECONDS);
    }

    private static void onErrorHandler(Exception error) {
        System.out.println("WS error: " + error.getMessage());
        logError("WS error: " + error.getMessage());
    }

    private static void onCloseHandler() {
        System.out.println("### connection closed ###");
        logInfo("connection closed");
    }

    private static void logInfo(String message) {
        String logMessage = getTimestamp() + " INFO " + message;
        logWriter.println(logMessage);
        logWriter.flush();
    }

    private static void logWarning(String message) {
        String logMessage = getTimestamp() + " WARNING " + message;
        logWriter.println(logMessage);
        logWriter.flush();
    }

    private static void logError(String message) {
        String logMessage = getTimestamp() + " ERROR " + message;
        logWriter.println(logMessage);
        logWriter.flush();
    }

    private static String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}

class SBEBestOBRpiParser {
    private static final int HEADER_SIZE = 8; // 4 * uint16
    private static final int BODY_SIZE = 12 * 8 + 2; // 12 * int64 + 2 * int8
    private static final int TARGET_TEMPLATE_ID = 20000;

    public Map<String, Object> parse(byte[] data) {
        Map<String, Object> header = parseHeader(data);
        if ((int) header.get("template_id") != TARGET_TEMPLATE_ID) {
            throw new RuntimeException("Unsupported template_id: " + header.get("template_id"));
        }

        if (data.length < HEADER_SIZE + BODY_SIZE) {
            throw new RuntimeException("Insufficient data for BestOBRpiEvent body");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE);

        long ts = buffer.getLong();
        long seq = buffer.getLong();
        long cts = buffer.getLong();
        long u = buffer.getLong();
        long askNpM = buffer.getLong();
        long askNsM = buffer.getLong();
        long askRpM = buffer.getLong();
        long askRsM = buffer.getLong();
        long bidNpM = buffer.getLong();
        long bidNsM = buffer.getLong();
        long bidRpM = buffer.getLong();
        long bidRsM = buffer.getLong();
        byte priceExp = buffer.get();
        byte sizeExp = buffer.get();

        int offset = HEADER_SIZE + BODY_SIZE;
        VarStringResult symbolResult = parseVarString8(data, offset);
        String symbol = symbolResult.string;
        offset = symbolResult.offset;

        double askNp = applyExponent(askNpM, priceExp);
        double askNs = applyExponent(askNsM, sizeExp);
        double askRp = applyExponent(askRpM, priceExp);
        double askRs = applyExponent(askRsM, sizeExp);
        double bidNp = applyExponent(bidNpM, priceExp);
        double bidNs = applyExponent(bidNsM, sizeExp);
        double bidRp = applyExponent(bidRpM, priceExp);
        double bidRs = applyExponent(bidRsM, sizeExp);

        Map<String, Object> result = new HashMap<>();
        result.put("header", header);
        result.put("ts", ts);
        result.put("seq", seq);
        result.put("cts", cts);
        result.put("u", u);
        result.put("price_exponent", priceExp);
        result.put("size_exponent", sizeExp);
        result.put("symbol", symbol);
        result.put("ask_normal_price", askNp);
        result.put("ask_normal_size", askNs);
        result.put("ask_rpi_price", askRp);
        result.put("ask_rpi_size", askRs);
        result.put("bid_normal_price", bidNp);
        result.put("bid_normal_size", bidNs);
        result.put("bid_rpi_price", bidRp);
        result.put("bid_rpi_size", bidRs);
        result.put("parsed_length", offset);

        return result;
    }

    private Map<String, Object> parseHeader(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new RuntimeException("Insufficient data for SBE header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int blockLength = buffer.getShort() & 0xFFFF;
        int templateId = buffer.getShort() & 0xFFFF;
        int schemaId = buffer.getShort() & 0xFFFF;
        int version = buffer.getShort() & 0xFFFF;

        Map<String, Object> header = new HashMap<>();
        header.put("block_length", blockLength);
        header.put("template_id", templateId);
        header.put("schema_id", schemaId);
        header.put("version", version);

        return header;
    }

    private VarStringResult parseVarString8(byte[] data, int offset) {
        if (offset + 1 > data.length) {
            throw new RuntimeException("Insufficient data for varString8 length");
        }

        int length = data[offset] & 0xFF;
        offset++;

        if (offset + length > data.length) {
            throw new RuntimeException("Insufficient data for varString8 bytes");
        }

        String string = new String(data, offset, length);
        offset += length;

        return new VarStringResult(string, offset);
    }

    private double applyExponent(long value, int exponent) {
        if (exponent >= 0) {
            return (double) value / Math.pow(10, exponent);
        } else {
            return (double) value * Math.pow(10, -exponent);
        }
    }

    private static class VarStringResult {
        public final String string;
        public final int offset;

        public VarStringResult(String string, int offset) {
            this.string = string;
            this.offset = offset;
        }
    }
}
