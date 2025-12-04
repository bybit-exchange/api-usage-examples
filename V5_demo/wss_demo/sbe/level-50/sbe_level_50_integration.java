package sbe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.framing.PingFrame;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

// -------------------------------------------------------------------
// Config
// -------------------------------------------------------------------
class Config {
    // L50 SBE order book topic
    public static final String TOPIC = "ob.50.sbe.BTCUSDT";

    // Adjust URL for spot / contract environment as needed:
    public static final String WS_URL = "wss://stream-testnet.bybits.org/v5/public-sbe/spot";
}

// -------------------------------------------------------------------
// Logging setup
// -------------------------------------------------------------------
class CustomLogger {
    private final PrintWriter logWriter;
    private final SimpleDateFormat dateFormat;

    public CustomLogger(String filename) throws IOException {
        this.logWriter = new PrintWriter(new FileWriter(filename, true), true);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    }

    public void log(String level, String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("%s %s %s", timestamp, level, message);

        logWriter.println(logMessage);
        System.out.println("[" + level + "] " + message);
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void warning(String message) {
        log("WARNING", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    public void exception(Exception error, String context) {
        String message = String.format("%s: %s\n%s", context, error.getMessage(), getStackTrace(error));
        log("ERROR", message);
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    public void close() {
        if (logWriter != null) {
            logWriter.close();
        }
    }
}

// -------------------------------------------------------------------
// SBE Parser for OBL50Event (template_id = 20001)
// -------------------------------------------------------------------
class SBEOBL50Parser {
    // message header: blockLength, templateId, schemaId, version
    private final int headerSz = 8; // 4 * uint16 = 8 bytes

    // fixed body fields: 4 x int64 + 2 x int8 + 1 x uint8
    private final int bodySz = 4 * 8 + 2 * 1 + 1; // 35 bytes

    // group header for repeating groups: blockLength(uint16), numInGroup(uint16)
    private final int groupHdrSz = 4; // 2 * uint16 = 4 bytes

    // each group entry: price(int64), size(int64)
    private final int levelSz = 16; // 2 * int64 = 16 bytes

    private final int targetTemplateId = 20001;

    // ---------------- core small helpers ----------------

    private Map<String, Object> parseHeader(byte[] data, int offset) throws Exception {
        if (data.length < offset + headerSz) {
            throw new Exception("insufficient data for SBE header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, headerSz).order(ByteOrder.LITTLE_ENDIAN);

        int blockLength = buffer.getShort() & 0xFFFF;
        int templateId = buffer.getShort() & 0xFFFF;
        int schemaId = buffer.getShort() & 0xFFFF;
        int version = buffer.getShort() & 0xFFFF;

        Map<String, Object> result = new HashMap<>();
        result.put("blockLength", blockLength);
        result.put("templateId", templateId);
        result.put("schemaId", schemaId);
        result.put("version", version);
        result.put("offset", offset + headerSz);

        return result;
    }

    private Map<String, Object> parseVarString8(byte[] data, int offset) throws Exception {
        if (offset + 1 > data.length) {
            throw new Exception("insufficient data for varString8 length");
        }

        int length = data[offset] & 0xFF;
        offset += 1;

        if (length == 0) {
            Map<String, Object> result = new HashMap<>();
            result.put("string", "");
            result.put("offset", offset);
            return result;
        }

        if (offset + length > data.length) {
            throw new Exception("insufficient data for varString8 bytes");
        }

        String string = new String(data, offset, length, StandardCharsets.UTF_8);
        offset += length;

        Map<String, Object> result = new HashMap<>();
        result.put("string", string);
        result.put("offset", offset);
        return result;
    }

    private double applyExponent(long value, int exponent) {
        if (exponent >= 0) {
            return (double) value / Math.pow(10, exponent);
        } else {
            return (double) value * Math.pow(10, -exponent);
        }
    }

    private double applyExponent(BigInteger value, int exponent) {
        if (exponent >= 0) {
            return value.doubleValue() / Math.pow(10, exponent);
        } else {
            return value.doubleValue() * Math.pow(10, -exponent);
        }
    }

    private Map<String, Object> parseLevels(byte[] data, int offset) throws Exception {
        if (offset + groupHdrSz > data.length) {
            throw new Exception("insufficient data for group header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, groupHdrSz).order(ByteOrder.LITTLE_ENDIAN);
        int blockLength = buffer.getShort() & 0xFFFF;
        int numInGroup = buffer.getShort() & 0xFFFF;
        offset += groupHdrSz;

        if (blockLength < levelSz) {
            throw new Exception("blockLength(" + blockLength + ") < levelSz(" + levelSz + ")");
        }

        List<Map<String, Object>> levels = new ArrayList<>();
        for (int i = 0; i < numInGroup; i++) {
            if (offset + blockLength > data.length) {
                throw new Exception("insufficient data for group entry");
            }

            // Read price and size (first 16 bytes of the block)
            ByteBuffer levelBuffer = ByteBuffer.wrap(data, offset, 16).order(ByteOrder.LITTLE_ENDIAN);
            long priceM = levelBuffer.getLong();
            long sizeM = levelBuffer.getLong();
            offset += blockLength; // skip the whole block

            Map<String, Object> level = new HashMap<>();
            level.put("priceM", priceM);
            level.put("sizeM", sizeM);
            levels.add(level);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("levels", levels);
        result.put("offset", offset);
        return result;
    }

    // ---------------- public parse ----------------

    public Map<String, Object> parse(byte[] data) throws Exception {
        Map<String, Object> headerResult = parseHeader(data, 0);
        int templateId = (Integer) headerResult.get("templateId");

        if (templateId != targetTemplateId) {
            return null;
        }

        int offset = (Integer) headerResult.get("offset");

        if (offset + bodySz > data.length) {
            throw new Exception("insufficient data for OBL50Event body");
        }

        // Parse fixed body
        ByteBuffer bodyBuffer = ByteBuffer.wrap(data, offset, bodySz).order(ByteOrder.LITTLE_ENDIAN);
        long ts = bodyBuffer.getLong();
        long seq = bodyBuffer.getLong();
        long cts = bodyBuffer.getLong();
        long u = bodyBuffer.getLong();
        byte priceExp = bodyBuffer.get();
        byte sizeExp = bodyBuffer.get();
        byte pkgType = bodyBuffer.get();

        offset += bodySz;

        // Parse asks group
        Map<String, Object> asksResult = parseLevels(data, offset);
        List<Map<String, Object>> asksRaw = (List<Map<String, Object>>) asksResult.get("levels");
        offset = (Integer) asksResult.get("offset");

        // Parse bids group
        Map<String, Object> bidsResult = parseLevels(data, offset);
        List<Map<String, Object>> bidsRaw = (List<Map<String, Object>>) bidsResult.get("levels");
        offset = (Integer) bidsResult.get("offset");

        // Parse symbol
        Map<String, Object> symbolResult = parseVarString8(data, offset);
        String symbol = (String) symbolResult.get("string");
        offset = (Integer) symbolResult.get("offset");

        // Apply exponents
        List<Map<String, Object>> asks = new ArrayList<>();
        for (Map<String, Object> rawLevel : asksRaw) {
            long priceM = (Long) rawLevel.get("priceM");
            long sizeM = (Long) rawLevel.get("sizeM");

            Map<String, Object> level = new HashMap<>();
            level.put("price", applyExponent(priceM, priceExp));
            level.put("size", applyExponent(sizeM, sizeExp));
            asks.add(level);
        }

        List<Map<String, Object>> bids = new ArrayList<>();
        for (Map<String, Object> rawLevel : bidsRaw) {
            long priceM = (Long) rawLevel.get("priceM");
            long sizeM = (Long) rawLevel.get("sizeM");

            Map<String, Object> level = new HashMap<>();
            level.put("price", applyExponent(priceM, priceExp));
            level.put("size", applyExponent(sizeM, sizeExp));
            bids.add(level);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("header", headerResult);
        result.put("ts", String.valueOf(ts));
        result.put("seq", String.valueOf(seq));
        result.put("cts", String.valueOf(cts));
        result.put("u", String.valueOf(u));
        result.put("priceExponent", (int) priceExp);
        result.put("sizeExponent", (int) sizeExp);
        result.put("pkgType", (int) pkgType); // 0 = SNAPSHOT, 1 = DELTA
        result.put("symbol", symbol);
        result.put("asks", asks);
        result.put("bids", bids);
        result.put("parsedLength", offset);

        return result;
    }
}

// -------------------------------------------------------------------
// WebSocket Client
// -------------------------------------------------------------------
class BybitWebSocketClient extends WebSocketClient {
    private final CustomLogger logger;
    private final ObjectMapper objectMapper;
    private final SBEOBL50Parser parser;
    private final ScheduledExecutorService pingScheduler;

    public BybitWebSocketClient(CustomLogger logger) throws URISyntaxException {
        super(new URI(Config.WS_URL));
        this.logger = logger;
        this.objectMapper = new ObjectMapper();
        this.parser = new SBEOBL50Parser();
        this.pingScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("WebSocket connection opened");
        System.out.println("opened");

        // Subscribe to topic
        try {
            Map<String, Object> sub = new HashMap<>();
            sub.put("op", "subscribe");
            sub.put("args", new String[]{Config.TOPIC});

            String subJson = objectMapper.writeValueAsString(sub);
            send(subJson);
            logger.info("subscribed: " + Config.TOPIC);
            System.out.println("subscribed: " + Config.TOPIC);
        } catch (Exception e) {
            logger.exception(e, "Failed to create subscription JSON");
        }

        // Start ping scheduler
        pingScheduler.scheduleAtFixedRate(this::sendPing1, 10, 10, TimeUnit.SECONDS);

        // Also send WebSocket ping frames
        pingScheduler.scheduleAtFixedRate(this::sendWebSocketPing, 20, 20, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> obj = objectMapper.readValue(message, Map.class);
            logger.info("TEXT " + objectMapper.writeValueAsString(obj));
            System.out.println("TEXT: " + obj);
        } catch (JsonProcessingException e) {
            logger.warning("non-JSON text frame: " + message);
            System.out.println("TEXT(non-json): " + message);
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);

            Map<String, Object> decoded = parser.parse(data);
            if (decoded != null) {
                processOrderBookMessage(decoded);
            }
        } catch (Exception e) {
            logger.exception(e, "decode error");
            System.out.println("decode error: " + e.getMessage());
        }
    }

    private void processOrderBookMessage(Map<String, Object> decoded) {
        try {
            int pkgType = (Integer) decoded.get("pkgType");
            String pkgStr = (pkgType == 0) ? "SNAPSHOT" : (pkgType == 1) ? "DELTA" : "UNKNOWN(" + pkgType + ")";

            List<Map<String, Object>> asks = (List<Map<String, Object>>) decoded.get("asks");
            List<Map<String, Object>> bids = (List<Map<String, Object>>) decoded.get("bids");

            Map<String, Object> bestAsk = createDefaultLevel();
            Map<String, Object> bestBid = createDefaultLevel();

            if (!asks.isEmpty()) {
                bestAsk = asks.get(0);
            }
            if (!bids.isEmpty()) {
                bestBid = bids.get(0);
            }

            String logMessage = String.format(
                    "SBE %s u=%s seq=%s type=%s asks=%d bids=%d " +
                            "BEST bid=%.8f@%.8f ask=%.8f@%.8f ts=%s",
                    decoded.get("symbol"), decoded.get("u"), decoded.get("seq"), pkgStr,
                    asks.size(), bids.size(),
                    (Double) bestBid.get("price"), (Double) bestBid.get("size"),
                    (Double) bestAsk.get("price"), (Double) bestAsk.get("size"),
                    decoded.get("ts")
            );

            logger.info(logMessage);

            System.out.printf("%s  u=%s  seq=%s  %s  levels: asks=%d bids=%d  " +
                            "BEST: bid %.8f x %.8f  |  ask %.8f x %.8f%n",
                    decoded.get("symbol"), decoded.get("u"), decoded.get("seq"), pkgStr,
                    asks.size(), bids.size(),
                    (Double) bestBid.get("price"), (Double) bestBid.get("size"),
                    (Double) bestAsk.get("price"), (Double) bestAsk.get("size"));
        } catch (Exception e) {
            logger.exception(e, "Error processing order book message");
        }
    }

    private Map<String, Object> createDefaultLevel() {
        Map<String, Object> level = new HashMap<>();
        level.put("price", 0.0);
        level.put("size", 0.0);
        return level;
    }

    private void sendPing1() {
        try {
            Map<String, String> ping = new HashMap<>();
            ping.put("op", "ping");

            String pingJson = objectMapper.writeValueAsString(ping);
            send(pingJson);
        } catch (Exception e) {
            logger.warning("Failed to send ping: " + e.getMessage());
        }
    }

    private void sendWebSocketPing() {
        try {
            if (isOpen()) {
                sendPing1(); // Send empty WebSocket ping frame
            }
        } catch (Exception e) {
            logger.warning("Failed to send WebSocket ping: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("connection closed: code=" + code + ", reason=" + reason);
        System.out.println("### connection closed ###");
        pingScheduler.shutdown();
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WS error: " + ex.getMessage());
        System.out.println("WS error: " + ex.getMessage());
    }
}

// -------------------------------------------------------------------
// Main Application
// -------------------------------------------------------------------
public class sbe_level_50_integration {
    private static CustomLogger logger;

    static {
        try {
            logger = new CustomLogger("logfile_ob50.log");
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            BybitWebSocketClient client = new BybitWebSocketClient(logger);
            client.connect();

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                client.close();
                logger.close();
            }));

            // Keep the application running
            while (true) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            logger.exception(e, "Failed to start WebSocket connection");
            System.err.println("Failed to start WebSocket connection: " + e.getMessage());
            System.exit(1);
        }
    }
}
