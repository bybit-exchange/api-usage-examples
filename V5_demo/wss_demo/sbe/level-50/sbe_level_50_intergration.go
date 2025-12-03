// sbe_ob50_client.go
package main

import (
    "bytes"
    "compress/flate"
    "encoding/binary"
    "encoding/json"
    "fmt"
    "log"
    "math"
    "time"

    "github.com/gorilla/websocket"
    "yourmodule/quote" // generated SBE package
)

const (
    WSURL   = "wss://stream.bybit.com/v5/market/sbe"
    CHANNEL = "ob.50.sbe.BTCUSDT"
)

func toReal(mantissa int64, exponent int8) float64 {
    return float64(mantissa) * math.Pow10(int(exponent))
}

func decodeOBL50(buf []byte) (*quote.OBL50Event, error) {
    var hdr quote.MessageHeader
    reader := bytes.NewReader(buf)

    // decode messageHeader (little endian)
    if err := binary.Read(reader, binary.LittleEndian, &hdr); err != nil {
        return nil, fmt.Errorf("read header: %w", err)
    }

    if hdr.TemplateId != 20001 {
        return nil, fmt.Errorf("unexpected templateId: %d", hdr.TemplateId)
    }

    var msg quote.OBL50Event
    // many generators provide WrapForDecode; here assume we can read the fixed block then groups
    if err := msg.Decode(reader, int(hdr.BlockLength), int(hdr.Version)); err != nil {
        return nil, fmt.Errorf("decode OBL50: %w", err)
    }

    return &msg, nil
}

func main() {
    book := NewOrderBook()

    dialer := websocket.Dialer{
        HandshakeTimeout: 10 * time.Second,
        EnableCompression: false,
    }

    conn, _, err := dialer.Dial(WSURL, nil)
    if err != nil {
        log.Fatalf("dial: %v", err)
    }
    defer conn.Close()

    // subscribe
    sub := map[string]interface{}{
        "op":   "subscribe",
        "args": []string{CHANNEL},
    }
    if err := conn.WriteJSON(sub); err != nil {
        log.Fatalf("subscribe: %v", err)
    }

    for {
        mt, data, err := conn.ReadMessage()
        if err != nil {
            log.Fatalf("read: %v", err)
        }

        if mt == websocket.TextMessage {
            // control JSON or pong etc
            var m map[string]interface{}
            _ = json.Unmarshal(data, &m)
            continue
        }

        // if server wraps SBE in per-message deflate, you may need to decompress:
        if isDeflatedFrame(data) {
            data, err = inflate(data)
            if err != nil {
                log.Printf("inflate error: %v", err)
                continue
            }
        }

        msg, err := decodeOBL50(data)
        if err != nil {
            log.Printf("decode error: %v", err)
            continue
        }

        u := msg.U
        pkgType := msg.PkgType // 0 snapshot, 1 delta
        pxExp := msg.PriceExponent
        szExp := msg.SizeExponent

        // extract levels
        var asks, bids [][2]float64
        for _, a := range msg.Asks {
            p := toReal(a.Price, pxExp)
            sz := toReal(a.Size, szExp)
            asks = append(asks, [2]float64{p, sz})
        }
        for _, b := range msg.Bids {
            p := toReal(b.Price, pxExp)
            sz := toReal(b.Size, szExp)
            bids = append(bids, [2]float64{p, sz})
        }

        // continuity logic:
        if u == 1 {
            // service restart / precision change snapshot
            book.Asks.SnapshotFrom(asks)
            book.Bids.SnapshotFrom(bids)
            book.LastU = 1
            fmt.Printf("[RESET SNAPSHOT] u=%d seq=%d symbol=%s\n", u, msg.Seq, msg.Symbol)
            continue
        }

        if book.LastU != 0 && u != book.LastU+1 {
            log.Printf("[WARN] u jump: lastU=%d newU=%d – consider resync", book.LastU, u)
        }

        if pkgType == quote.PkgTypeEnum_SNAPSHOT {
            book.Asks.SnapshotFrom(asks)
            book.Bids.SnapshotFrom(bids)
        } else {
            for _, lv := range asks {
                book.Asks.Apply(lv[0], lv[1])
            }
            for _, lv := range bids {
                book.Bids.Apply(lv[0], lv[1])
            }
        }

        book.LastU = u
        bestBid := book.Bids.BestBid()
        bestAsk := book.Asks.BestAsk()
        fmt.Printf("u=%d pkgType=%d bestBid=%.5f bestAsk=%.5f\n", u, pkgType, bestBid, bestAsk)
    }
}

// helpers (optional, depending on ws framing)
func isDeflatedFrame(data []byte) bool {
    // placeholder: detect by protocol; many setups know from WS sub-protocol
    return false
}

func inflate(data []byte) ([]byte, error) {
    r := flate.NewReader(bytes.NewReader(data))
    defer r.Close()

    var out bytes.Buffer
    if _, err := out.ReadFrom(r); err != nil {
        return nil, err
    }
    return out.Bytes(), nil
}