package main

import (
    "context"
    "crypto/hmac"
    "crypto/sha256"
    "encoding/binary"
    "encoding/hex"
    "encoding/json"
    "flag"
    "fmt"
    "log"
    "math"
    "os"
    "os/signal"
    "time"

    "github.com/gorilla/websocket"
)

// ---------- Config ----------

const (
    MMWSURLTestnetBybits = "wss://stream-testnet.bybits.org/v5/private"
)

// TODO: fill in your real keys
const (
    APIKey    = "xxx"
    APISecret = "xxxxx"
)

var subTopics = []string{
    "order.sbe.resp.linear",
}

// ---------- SBE helpers ----------

func readU8(buf []byte, off *int) (uint8, error) {
    if *off+1 > len(buf) {
        return 0, fmt.Errorf("readU8: out of range")
    }
    v := buf[*off]
    *off++
    return v, nil
}

func readI8(buf []byte, off *int) (int8, error) {
    if *off+1 > len(buf) {
        return 0, fmt.Errorf("readI8: out of range")
    }
    v := int8(buf[*off])
    *off++
    return v, nil
}

func readU16LE(buf []byte, off *int) (uint16, error) {
    if *off+2 > len(buf) {
        return 0, fmt.Errorf("readU16LE: out of range")
    }
    v := binary.LittleEndian.Uint16(buf[*off : *off+2])
    *off += 2
    return v, nil
}

func readI64LE(buf []byte, off *int) (int64, error) {
    if *off+8 > len(buf) {
        return 0, fmt.Errorf("readI64LE: out of range")
    }
    v := int64(binary.LittleEndian.Uint64(buf[*off : *off+8]))
    *off += 8
    return v, nil
}

func readVarString8(buf []byte, off *int) (string, error) {
    if *off+1 > len(buf) {
        return "", fmt.Errorf("readVarString8: no length byte")
    }
    ln := int(buf[*off])
    *off++
    if ln == 0 {
        return "", nil
    }
    if *off+ln > len(buf) {
        return "", fmt.Errorf("readVarString8: length out of range")
    }
    s := string(buf[*off : *off+ln])
    *off += ln
    return s, nil
}

// applyExp replicates the Python apply_exp(mantissa, exp)
func applyExp(mantissa int64, exp int8) float64 {
    e := int(exp)
    if e >= 0 {
        return float64(mantissa) / math.Pow10(e)
    }
    return float64(mantissa) * math.Pow10(-e)
}

// ---------- Fast Order SBE decode ----------

type FastOrderSBEResp struct {
    SBEHeader struct {
        BlockLength uint16 `json:"blockLength"`
        TemplateID  uint16 `json:"templateId"`
        SchemaID    uint16 `json:"schemaId"`
        Version     uint16 `json:"version"`
    } `json:"_sbe_header"`

    Category            uint8  `json:"category"`
    Side                uint8  `json:"side"`
    OrderStatus         uint8  `json:"orderStatus"`
    PriceExponent       int8   `json:"priceExponent"`
    SizeExponent        int8   `json:"sizeExponent"`
    ValExponent         int8   `json:"valueExponent"`
    RejectReason        uint16 `json:"rejectReason"`
    PriceMantissa       int64  `json:"priceMantissa"`
    QtyMantissa         int64  `json:"qtyMantissa"`
    LeavesQtyMantissa   int64  `json:"leavesQtyMantissa"`
    ValueMantissa       int64  `json:"valueMantissa"`
    LeavesValueMantissa int64  `json:"leavesValueMantissa"`
    CreationTime        int64  `json:"creationTime"`
    UpdatedTime         int64  `json:"updatedTime"`
    Seq                 int64  `json:"seq"`
    SymbolName          string `json:"symbolName"`
    OrderID             string `json:"orderId"`
    OrderLinkID         string `json:"orderLinkId"`

    Price       float64 `json:"price"`
    Qty         float64 `json:"qty"`
    LeavesQty   float64 `json:"leavesQty"`
    Value       float64 `json:"value"`
    LeavesValue float64 `json:"leavesValue"`

    RawOffsetEnd int `json:"_raw_offset_end"`
}

func decodeFastOrderResp(payload []byte, debug bool) (*FastOrderSBEResp, error) {
    if len(payload) < 8 {
        return nil, fmt.Errorf("payload too short for SBE header")
    }

    off := 0
    blockLen := binary.LittleEndian.Uint16(payload[off : off+2])
    templateID := binary.LittleEndian.Uint16(payload[off+2 : off+4])
    schemaID := binary.LittleEndian.Uint16(payload[off+4 : off+6])
    version := binary.LittleEndian.Uint16(payload[off+6 : off+8])
    off += 8

    if debug {
        log.Printf("HEADER: block_len=%d, template_id=%d, schema_id=%d, version=%d",
            blockLen, templateID, schemaID, version)
    }

    // Only handle template 21000 for now
    if templateID != 21000 {
        return nil, fmt.Errorf("unexpected templateId: %d", templateID)
    }

    var err error
    resp := &FastOrderSBEResp{}
    resp.SBEHeader.BlockLength = blockLen
    resp.SBEHeader.TemplateID = templateID
    resp.SBEHeader.SchemaID = schemaID
    resp.SBEHeader.Version = version

    // fixed fields in order
    if resp.Category, err = readU8(payload, &off); err != nil {
        return nil, err
    }
    if resp.Side, err = readU8(payload, &off); err != nil {
        return nil, err
    }
    if resp.OrderStatus, err = readU8(payload, &off); err != nil {
        return nil, err
    }
    if resp.PriceExponent, err = readI8(payload, &off); err != nil {
        return nil, err
    }
    if resp.SizeExponent, err = readI8(payload, &off); err != nil {
        return nil, err
    }
    if resp.ValExponent, err = readI8(payload, &off); err != nil {
        return nil, err
    }
    if resp.RejectReason, err = readU16LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.PriceMantissa, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.QtyMantissa, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.LeavesQtyMantissa, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.ValueMantissa, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.LeavesValueMantissa, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.CreationTime, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.UpdatedTime, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }
    if resp.Seq, err = readI64LE(payload, &off); err != nil {
        return nil, err
    }

    // strings
    if resp.SymbolName, err = readVarString8(payload, &off); err != nil {
        return nil, err
    }
    if resp.OrderID, err = readVarString8(payload, &off); err != nil {
        return nil, err
    }
    if resp.OrderLinkID, err = readVarString8(payload, &off); err != nil {
        return nil, err
    }

    // derived fields
    resp.Price = applyExp(resp.PriceMantissa, resp.PriceExponent)
    resp.Qty = applyExp(resp.QtyMantissa, resp.SizeExponent)
    resp.LeavesQty = applyExp(resp.LeavesQtyMantissa, resp.SizeExponent)
    resp.Value = applyExp(resp.ValueMantissa, resp.ValExponent)
    resp.LeavesValue = applyExp(resp.LeavesValueMantissa, resp.ValExponent)
    resp.RawOffsetEnd = off

    return resp, nil
}

// ---------- WebSocket helpers ----------

func sendJSON(conn *websocket.Conn, v interface{}) error {
    data, err := json.Marshal(v)
    if err != nil {
        return err
    }
    return conn.WriteMessage(websocket.TextMessage, data)
}

func signAuth(secret, value string) string {
    h := hmac.New(sha256.New, []byte(secret))
    h.Write([]byte(value))
    return hex.EncodeToString(h.Sum(nil))
}

func heartbeat(ctx context.Context, conn *websocket.Conn) {
    ticker := time.NewTicker(10 * time.Second)
    defer ticker.Stop()

    for {
        select {
        case <-ctx.Done():
            return
        case <-ticker.C:
            reqID := fmt.Sprintf("%d", time.Now().UnixMilli())
            err := sendJSON(conn, map[string]interface{}{
                "req_id": reqID,
                "op":     "ping",
            })
            if err != nil {
                log.Printf("[heartbeat] error sending ping: %v", err)
                return
            }
        }
    }
}

// ---------- Main run ----------

func run(ctx context.Context, url string) error {
    dialer := websocket.Dialer{
        HandshakeTimeout:  10 * time.Second,
        EnableCompression: false,
    }

    conn, _, err := dialer.Dial(url, nil)
    if err != nil {
        return fmt.Errorf("dial error: %w", err)
    }
    defer conn.Close()
    log.Printf("Connected to %s", url)

    // auth
    expires := (time.Now().Unix() + 10000) * 1000
    val := fmt.Sprintf("GET/realtime%d", expires)
    sig := signAuth(APISecret, val)

    authMsg := map[string]interface{}{
        "req_id": "10001",
        "op":     "auth",
        "args":   []interface{}{APIKey, expires, sig},
    }
    if err := sendJSON(conn, authMsg); err != nil {
        return fmt.Errorf("send auth error: %w", err)
    }

    // auth ack
    if _, msg, err := conn.ReadMessage(); err != nil {
        return fmt.Errorf("read auth ack error: %w", err)
    } else {
        log.Printf("auth-ack: %s", string(msg))
    }

    // subscribe
    subMsg := map[string]interface{}{
        "op":   "subscribe",
        "args": subTopics,
    }
    if err := sendJSON(conn, subMsg); err != nil {
        return fmt.Errorf("send subscribe error: %w", err)
    }

    // heartbeat
    hbCtx, hbCancel := context.WithCancel(ctx)
    defer hbCancel()
    go heartbeat(hbCtx, conn)

    // read loop
    for {
        select {
        case <-ctx.Done():
            log.Printf("context canceled, exit read loop")
            return nil
        default:
        }

        mt, data, err := conn.ReadMessage()
        if err != nil {
            return fmt.Errorf("read message error: %w", err)
        }

        switch mt {
        case websocket.BinaryMessage:
            resp, err := decodeFastOrderResp(data, false)
            if err != nil {
                log.Printf("binary decode error: %v", err)
            } else {
                j, _ := json.Marshal(resp)
                log.Printf("FAST_ORDER_SBE: %s", string(j))
            }
        case websocket.TextMessage:
            var obj map[string]interface{}
            if err := json.Unmarshal(data, &obj); err != nil {
                log.Printf("text-nonjson: %s", string(data))
                continue
            }
            if op, ok := obj["op"].(string); ok && op == "pong" {
                // ignore pong
                continue
            }
            j, _ := json.Marshal(obj)
            log.Printf("control: %s", string(j))
        default:
            log.Printf("unknown message type %d", mt)
        }
    }
}

// ---------- Entry ----------

func main() {
    url := flag.String("url", MMWSURLTestnetBybits, "WebSocket URL")
    flag.Parse()

    if APIKey == "YOUR_API_KEY" || APISecret == "YOUR_API_SECRET" {
        log.Println("⚠️ Please set APIKey and APISecret in the source before running.")
    }

    ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
    defer cancel()

    if err := run(ctx, *url); err != nil {
        log.Fatalf("run error: %v", err)
    }
}